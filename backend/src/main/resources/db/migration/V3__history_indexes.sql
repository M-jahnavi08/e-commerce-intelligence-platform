CREATE INDEX inventory_movements_product_history_idx
 ON inventory_movements(product_id, created_at DESC, id DESC);
CREATE INDEX orders_customer_history_idx
 ON orders(user_id, created_at DESC, id DESC);
CREATE INDEX orders_admin_history_idx
 ON orders(created_at DESC, id DESC);
