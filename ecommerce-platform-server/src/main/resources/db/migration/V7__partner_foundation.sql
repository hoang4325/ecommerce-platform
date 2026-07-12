INSERT INTO roles (name)
SELECT 'PARTNER'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'PARTNER');

CREATE TABLE partners (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    business_name VARCHAR(255) NOT NULL,
    tax_code VARCHAR(100),
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    address VARCHAR(500),
    status VARCHAR(30) NOT NULL,
    applicant_user_id BIGINT NOT NULL,
    approved_at DATETIME(6),
    approved_by BIGINT,
    rejected_at DATETIME(6),
    rejection_reason VARCHAR(1000),
    suspended_at DATETIME(6),
    suspension_reason VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_partner_code (code),
    UNIQUE KEY uk_partner_tax_code (tax_code),
    KEY idx_partner_status_created (status, created_at),
    KEY idx_partner_applicant (applicant_user_id),
    CONSTRAINT fk_partner_applicant FOREIGN KEY (applicant_user_id) REFERENCES users(id),
    CONSTRAINT fk_partner_approved_by FOREIGN KEY (approved_by) REFERENCES users(id),
    CONSTRAINT chk_partner_status CHECK (status IN ('DRAFT','PENDING_REVIEW','CHANGES_REQUESTED','APPROVED','REJECTED','SUSPENDED','TERMINATED'))
) ENGINE=InnoDB;

CREATE TABLE partner_members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    partner_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    joined_at DATETIME(6),
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_partner_member_user (partner_id, user_id),
    KEY idx_partner_member_user_status (user_id, status),
    CONSTRAINT fk_partner_member_partner FOREIGN KEY (partner_id) REFERENCES partners(id),
    CONSTRAINT fk_partner_member_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT chk_partner_member_role CHECK (role IN ('OWNER','MANAGER','PRODUCT_STAFF','ORDER_STAFF','FINANCE_STAFF')),
    CONSTRAINT chk_partner_member_status CHECK (status IN ('INVITED','ACTIVE','SUSPENDED','REMOVED'))
) ENGINE=InnoDB;

CREATE TABLE partner_documents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    partner_id BIGINT NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    checksum VARCHAR(128) NOT NULL,
    uploaded_by BIGINT NOT NULL,
    reviewed_by BIGINT,
    reviewed_at DATETIME(6),
    rejection_reason VARCHAR(1000),
    expires_at DATETIME(6),
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_partner_document_object_key (object_key),
    KEY idx_partner_document_partner_status (partner_id, status),
    CONSTRAINT fk_partner_document_partner FOREIGN KEY (partner_id) REFERENCES partners(id),
    CONSTRAINT fk_partner_document_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users(id),
    CONSTRAINT fk_partner_document_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users(id),
    CONSTRAINT chk_partner_document_size CHECK (file_size > 0)
) ENGINE=InnoDB;

CREATE TABLE partner_audit_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    partner_id BIGINT NOT NULL,
    action VARCHAR(100) NOT NULL,
    from_state VARCHAR(30),
    to_state VARCHAR(30),
    actor_user_id BIGINT,
    reason VARCHAR(1000),
    correlation_id VARCHAR(100),
    occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_partner_audit_partner_time (partner_id, occurred_at),
    CONSTRAINT fk_partner_audit_partner FOREIGN KEY (partner_id) REFERENCES partners(id),
    CONSTRAINT fk_partner_audit_actor FOREIGN KEY (actor_user_id) REFERENCES users(id)
) ENGINE=InnoDB;

INSERT INTO partners (
    code, name, business_name, tax_code, email, status, applicant_user_id,
    approved_at, version, created_at, updated_at
)
SELECT CONCAT('LEGACY-', u.id), u.username, u.username, NULL, u.email,
       'APPROVED', u.id, CURRENT_TIMESTAMP(6), 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM users u
JOIN user_roles ur ON ur.user_id = u.id
JOIN roles r ON r.id = ur.role_id AND r.name = 'SELLER'
WHERE NOT EXISTS (SELECT 1 FROM partners p WHERE p.applicant_user_id = u.id);

INSERT INTO partner_members (
    partner_id, user_id, role, status, joined_at, version, created_at, updated_at
)
SELECT p.id, p.applicant_user_id, 'OWNER', 'ACTIVE', CURRENT_TIMESTAMP(6), 0,
       CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM partners p
WHERE p.code LIKE 'LEGACY-%'
  AND NOT EXISTS (
      SELECT 1 FROM partner_members pm
      WHERE pm.partner_id = p.id AND pm.user_id = p.applicant_user_id
  );

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT pm.user_id, r.id
FROM partner_members pm
JOIN roles r ON r.name = 'PARTNER'
WHERE pm.status = 'ACTIVE';

