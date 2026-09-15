CREATE TABLE categories (
 id UUID PRIMARY KEY, name VARCHAR(100) NOT NULL UNIQUE
);
CREATE TABLE products (
 id UUID PRIMARY KEY, sku VARCHAR(64) NOT NULL UNIQUE, name VARCHAR(200) NOT NULL,
 description VARCHAR(4000) NOT NULL, category_id UUID NOT NULL REFERENCES categories(id),
 price NUMERIC(12,2) NOT NULL CHECK (price >= 0), active BOOLEAN NOT NULL DEFAULT TRUE,
 version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX products_category_idx ON products(category_id);
CREATE TABLE inventory (
 product_id UUID PRIMARY KEY REFERENCES products(id), quantity INTEGER NOT NULL CHECK (quantity >= 0),
 version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE users (
 id UUID PRIMARY KEY, email VARCHAR(254) NOT NULL UNIQUE, password_hash VARCHAR(100) NOT NULL,
 role VARCHAR(20) NOT NULL CHECK (role IN ('CUSTOMER','ADMIN')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE cart_items (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id),
 product_id UUID NOT NULL REFERENCES products(id), quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 100),
 UNIQUE(user_id, product_id)
);
CREATE TABLE orders (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id),
 idempotency_key UUID NOT NULL, status VARCHAR(30) NOT NULL CHECK (status IN ('PAID','PAYMENT_FAILED')),
 total NUMERIC(14,2) NOT NULL CHECK(total >= 0), created_at TIMESTAMPTZ NOT NULL,
 UNIQUE(user_id, idempotency_key)
);
CREATE INDEX orders_created_idx ON orders(created_at);
CREATE TABLE order_items (
 id UUID PRIMARY KEY, order_id UUID NOT NULL REFERENCES orders(id), product_id UUID NOT NULL REFERENCES products(id),
 product_name VARCHAR(200) NOT NULL, quantity INTEGER NOT NULL CHECK(quantity > 0),
 unit_price NUMERIC(12,2) NOT NULL CHECK(unit_price >= 0)
);
CREATE INDEX order_items_order_idx ON order_items(order_id);
CREATE TABLE inventory_movements (
 id UUID PRIMARY KEY, product_id UUID NOT NULL REFERENCES products(id), actor_id UUID NOT NULL REFERENCES users(id),
 delta INTEGER NOT NULL, reason VARCHAR(300) NOT NULL, created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE outbox_events (
 id UUID PRIMARY KEY, aggregate_id UUID NOT NULL, event_type VARCHAR(80) NOT NULL,
 payload TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL, published_at TIMESTAMPTZ
);
CREATE INDEX outbox_pending_idx ON outbox_events(created_at) WHERE published_at IS NULL;
CREATE TABLE processed_events (id UUID PRIMARY KEY, processed_at TIMESTAMPTZ NOT NULL);
CREATE TABLE sales_facts (
 order_id UUID NOT NULL REFERENCES orders(id), product_id UUID NOT NULL REFERENCES products(id),
 quantity INTEGER NOT NULL, revenue NUMERIC(14,2) NOT NULL, occurred_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY(order_id, product_id)
);
