-- The existing portfolio data used USD. This migration redenominates it once.
-- Demo SKUs receive curated INR prices. Other historical amounts use a fixed
-- demo conversion factor of 80; this is not a live or advertised exchange rate.
ALTER TABLE products ADD COLUMN image_url VARCHAR(300) NOT NULL DEFAULT '/images/products/placeholder.svg';
ALTER TABLE order_items ADD COLUMN image_url VARCHAR(300) NOT NULL DEFAULT '/images/products/placeholder.svg';
CREATE TEMPORARY TABLE inr_demo_prices (sku TEXT PRIMARY KEY, old_price NUMERIC, new_price NUMERIC, image_url TEXT) ON COMMIT DROP;
INSERT INTO inr_demo_prices VALUES
('DEMO-HEADPHONES', 129, 6999, '/images/products/headphones.svg'),
('DEMO-EARBUDS', 69, 2499, '/images/products/earbuds.svg'),
('DEMO-SPEAKER', 89, 3499, '/images/products/speaker.svg'),
('DEMO-KEYBOARD', 119, 4999, '/images/products/keyboard.svg'),
('DEMO-LAMP', 54, 1499, '/images/products/lamp.svg'),
('DEMO-STAND', 45, 1799, '/images/products/stand.svg'),
('DEMO-BAG', 79, 2499, '/images/products/bag.svg'),
('DEMO-BOTTLE', 28, 899, '/images/products/bottle.svg'),
('DEMO-NOTEBOOK', 18, 499, '/images/products/notebook.svg'),
('DEMO-MUG', 22, 599, '/images/products/mug.svg'),
('DEMO-THROW', 49, 1999, '/images/products/throw.svg'),
('DEMO-TRAY', 32, 999, '/images/products/tray.svg'),
('DEMO-HUB', 49, 1799, '/images/products/hub.svg');

UPDATE order_items i SET unit_price = CASE
  WHEN i.unit_price = d.old_price THEN d.new_price ELSE round(i.unit_price * 80, 2) END,
  image_url = d.image_url
FROM products p JOIN inr_demo_prices d ON p.sku = d.sku WHERE i.product_id = p.id;
UPDATE order_items i SET unit_price = round(i.unit_price * 80, 2)
FROM products p WHERE i.product_id = p.id AND NOT EXISTS (SELECT 1 FROM inr_demo_prices d WHERE d.sku = p.sku);
UPDATE products p SET price = d.new_price, image_url = d.image_url, version = version + 1
FROM inr_demo_prices d WHERE p.sku = d.sku;
UPDATE products p SET price = round(price * 80, 2), version = version + 1
WHERE NOT EXISTS (SELECT 1 FROM inr_demo_prices d WHERE d.sku = p.sku);
UPDATE orders o SET total = (SELECT coalesce(sum(i.quantity * i.unit_price), 0) FROM order_items i WHERE i.order_id = o.id);
UPDATE sales_facts f SET revenue = x.revenue FROM (
  SELECT order_id, product_id, sum(quantity * unit_price) AS revenue FROM order_items GROUP BY order_id, product_id
) x WHERE x.order_id = f.order_id AND x.product_id = f.product_id;
UPDATE outbox_events e SET payload = jsonb_set(jsonb_set(e.payload::jsonb, '{currency}', '"INR"'::jsonb), '{items}',
  (SELECT jsonb_agg(jsonb_build_object('id', i.id, 'orderId', i.order_id, 'productId', i.product_id,
   'productName', i.product_name, 'quantity', i.quantity, 'unitPrice', i.unit_price, 'imageUrl', i.image_url) ORDER BY i.product_id)
   FROM order_items i WHERE i.order_id = e.aggregate_id))::text
WHERE event_type = 'OrderPaid' AND EXISTS (SELECT 1 FROM order_items i WHERE i.order_id = e.aggregate_id);
