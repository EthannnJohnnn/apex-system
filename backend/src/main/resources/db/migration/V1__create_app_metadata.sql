CREATE TABLE app_metadata (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    application_name VARCHAR(64) NOT NULL,
    schema_version INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO app_metadata (id, application_name, schema_version)
VALUES (1, 'Apex', 1);
