# pyright: reportMissingImports=false

import os
import re
from contextlib import asynccontextmanager
from urllib.parse import urlparse

import httpx
import mistune
from fastapi import FastAPI, Request
from fastapi.responses import HTMLResponse, RedirectResponse, Response
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from markupsafe import Markup


BACKEND_URL = os.getenv("BACKEND_URL", "http://backend:8080")
_backend_netloc = urlparse(BACKEND_URL).netloc

templates = Jinja2Templates(directory="templates")


def _render_markdown(text: str | None) -> Markup:
    if not text:
        return Markup("")
    md = mistune.create_markdown(escape=True, hard_wrap=True)
    html = md(text)
    html = re.sub(
        r'<a\s+href=',
        '<a rel="nofollow noopener noreferrer" target="_blank" href=',
        html,
    )
    return Markup(html)


templates.env.filters["markdown"] = _render_markdown

_HOP_BY_HOP_HEADERS = {
    "connection",
    "keep-alive",
    "proxy-authenticate",
    "proxy-authorization",
    "te",
    "trailers",
    "transfer-encoding",
    "upgrade",
}


@asynccontextmanager
async def lifespan(app: FastAPI):
    timeout = httpx.Timeout(30.0)
    app.state.http_client = httpx.AsyncClient(base_url=BACKEND_URL, timeout=timeout)
    try:
        yield
    finally:
        await app.state.http_client.aclose()


app = FastAPI(lifespan=lifespan)

app.mount("/static", StaticFiles(directory="static"), name="static")


async def _get_current_user(request: Request) -> dict | None:
    token = request.cookies.get("access_token")
    if not token:
        return None
    client: httpx.AsyncClient = request.app.state.http_client
    try:
        resp = await client.get(
            "/api/auth/me", cookies={"access_token": token}
        )
        if resp.status_code == 200:
            return resp.json()
    except httpx.RequestError:
        pass
    return None


def _auth_cookies(request: Request) -> dict[str, str]:
    cookies: dict[str, str] = {}
    token = request.cookies.get("access_token")
    if token:
        cookies["access_token"] = token
    return cookies


# ── Public pages ──────────────────────────────────────────────


@app.get("/", response_class=HTMLResponse)
async def index(request: Request):
    user = await _get_current_user(request)
    client: httpx.AsyncClient = request.app.state.http_client
    posts = []
    try:
        resp = await client.get("/api/posts", params={"size": 50})
        if resp.status_code == 200:
            data = resp.json()
            posts = data.get("content", [])
    except httpx.RequestError:
        pass
    return templates.TemplateResponse(
        request, "index.html", {"request": request, "posts": posts, "user": user}
    )


@app.get("/posts/{post_id}", response_class=HTMLResponse)
async def post_detail(request: Request, post_id: int):
    user = await _get_current_user(request)
    client: httpx.AsyncClient = request.app.state.http_client
    cookies = _auth_cookies(request)

    try:
        resp = await client.get(f"/api/posts/{post_id}", cookies=cookies)
        if resp.status_code != 200:
            return templates.TemplateResponse(
                request, "error.html",
                {"request": request, "user": user, "message": "Post not found"},
                status_code=404,
            )
        post = resp.json()
        post["content_html"] = _render_markdown(post.get("contentMd", ""))
    except httpx.RequestError:
        return templates.TemplateResponse(
            request, "error.html",
            {"request": request, "user": user, "message": "Service unavailable"},
            status_code=502,
        )

    comments = []
    try:
        resp = await client.get(f"/api/posts/{post_id}/comments")
        if resp.status_code == 200:
            comments = resp.json()
    except httpx.RequestError:
        pass

    return templates.TemplateResponse(
        request, "post_detail.html",
        {"request": request, "post": post, "comments": comments, "user": user},
    )


# ── Auth pages ─────────────────────────────────────────────────


@app.get("/login", response_class=HTMLResponse)
async def login_page(request: Request):
    user = await _get_current_user(request)
    if user:
        return RedirectResponse(url="/", status_code=303)
    return templates.TemplateResponse(
        request, "login.html", {"request": request, "user": None}
    )


@app.get("/register", response_class=HTMLResponse)
async def register_page(request: Request):
    user = await _get_current_user(request)
    if user:
        return RedirectResponse(url="/", status_code=303)
    return templates.TemplateResponse(
        request, "register.html", {"request": request, "user": None}
    )


# ── Admin pages ────────────────────────────────────────────────


@app.get("/admin", response_class=HTMLResponse)
async def admin_dashboard(request: Request):
    user = await _get_current_user(request)
    if not user or user.get("role") != "admin":
        return RedirectResponse(url="/login", status_code=303)

    client: httpx.AsyncClient = request.app.state.http_client
    cookies = _auth_cookies(request)
    posts = []
    try:
        resp = await client.get("/api/admin/posts", cookies=cookies, params={"size": 50})
        if resp.status_code == 200:
            data = resp.json()
            posts = data.get("content", [])
    except httpx.RequestError:
        pass

    return templates.TemplateResponse(
        request, "admin/dashboard.html",
        {"request": request, "posts": posts, "user": user},
    )


@app.get("/admin/posts/new", response_class=HTMLResponse)
async def admin_new_post(request: Request):
    user = await _get_current_user(request)
    if not user or user.get("role") != "admin":
        return RedirectResponse(url="/login", status_code=303)
    return templates.TemplateResponse(
        request, "admin/post_form.html",
        {"request": request, "post": None, "user": user},
    )


@app.get("/admin/posts/{post_id}/edit", response_class=HTMLResponse)
async def admin_edit_post(request: Request, post_id: int):
    user = await _get_current_user(request)
    if not user or user.get("role") != "admin":
        return RedirectResponse(url="/login", status_code=303)

    client: httpx.AsyncClient = request.app.state.http_client
    cookies = _auth_cookies(request)
    try:
        resp = await client.get(f"/api/posts/{post_id}", cookies=cookies)
        if resp.status_code != 200:
            return templates.TemplateResponse(
                request, "error.html",
                {"request": request, "user": user, "message": "Post not found"},
                status_code=404,
            )
        post = resp.json()
    except httpx.RequestError:
        return templates.TemplateResponse(
            request, "error.html",
            {"request": request, "user": user, "message": "Service unavailable"},
            status_code=502,
        )

    return templates.TemplateResponse(
        request, "admin/post_form.html",
        {"request": request, "post": post, "user": user},
    )


@app.get("/admin/comments", response_class=HTMLResponse)
async def admin_comments(request: Request):
    user = await _get_current_user(request)
    if not user or user.get("role") != "admin":
        return RedirectResponse(url="/login", status_code=303)

    client: httpx.AsyncClient = request.app.state.http_client
    cookies = _auth_cookies(request)
    comments = []
    try:
        resp = await client.get("/api/admin/comments", cookies=cookies)
        if resp.status_code == 200:
            comments = resp.json()
    except httpx.RequestError:
        pass

    return templates.TemplateResponse(
        request, "admin/comments.html",
        {"request": request, "comments": comments, "user": user},
    )


# ── API proxy ──────────────────────────────────────────────────


def _build_upstream_headers(request: Request) -> dict[str, str]:
    headers: dict[str, str] = {}
    for name, value in request.headers.items():
        name_l = name.lower()
        if name_l in _HOP_BY_HOP_HEADERS:
            continue
        if name_l in {"host", "content-length", "accept-encoding"}:
            continue
        headers[name] = value

    original_host = request.headers.get("host", "")

    # Required behavior: set Host to backend while preserving original host info.
    if _backend_netloc:
        headers["host"] = _backend_netloc

    if original_host:
        headers["x-forwarded-host"] = original_host

    headers["x-forwarded-proto"] = request.url.scheme

    client_host = request.client.host if request.client else ""
    if client_host:
        existing_xff = request.headers.get("x-forwarded-for")
        headers["x-forwarded-for"] = (
            f"{existing_xff}, {client_host}" if existing_xff else client_host
        )

    return headers


@app.api_route(
    "/api/{path:path}",
    methods=["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"],
)
async def proxy_api(path: str, request: Request):
    upstream: httpx.AsyncClient = request.app.state.http_client

    body = await request.body()
    headers = _build_upstream_headers(request)
    upstream_path = f"/api/{path}"

    try:
        upstream_resp = await upstream.request(
            request.method,
            upstream_path,
            params=request.query_params,
            content=body if body else None,
            headers=headers,
        )
    except httpx.RequestError as exc:
        return Response(
            content=f"Upstream request failed: {exc.__class__.__name__}",
            status_code=502,
            media_type="text/plain",
        )

    response_headers: list[tuple[str, str]] = []
    for name_b, value_b in upstream_resp.headers.raw:
        name = name_b.decode("latin-1")
        value = value_b.decode("latin-1")
        name_l = name.lower()
        if name_l in _HOP_BY_HOP_HEADERS or name_l == "content-length":
            continue
        response_headers.append((name, value))

    resp = Response(
        content=upstream_resp.content,
        status_code=upstream_resp.status_code,
        media_type=None,
    )

    # Start clean: Starlette pre-populates some headers.
    if "content-type" in resp.headers:
        del resp.headers["content-type"]
    if "content-length" in resp.headers:
        del resp.headers["content-length"]

    for name, value in response_headers:
        resp.headers.append(name, value)

    return resp