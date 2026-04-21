CREATE TABLE ai_summary_logs (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    post_id BIGINT NOT NULL,
    admin_user_id BIGINT NULL,
    model TEXT NOT NULL,
    latency_ms INTEGER NULL,
    status TEXT NOT NULL,
    error_message TEXT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT ai_summary_logs_post_id_fk FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT ai_summary_logs_admin_user_id_fk FOREIGN KEY (admin_user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX ai_summary_logs_post_id_idx ON ai_summary_logs (post_id);
