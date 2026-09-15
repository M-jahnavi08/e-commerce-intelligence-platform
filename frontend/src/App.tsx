import { useEffect, useRef, useState, createContext, useContext } from 'react';
import type { FormEvent, ReactNode } from 'react';
import {
  Link,
  NavLink,
  Routes,
  Route,
  useNavigate,
  useParams,
  useSearchParams,
  Navigate,
} from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, money, errorMessage } from './api';
import type { Product, Category, Page } from './api';
type Session = {
  token: string;
  email: string;
  role: string;
  expiresAt: number;
};
const Auth = createContext<{
  session: Session | null;
  login: (s: Session) => void;
}>({ session: null, login: () => {} });
function ErrorBox({ error }: { error: unknown }) {
  return (
    <p className="notice error" role="alert">
      {errorMessage(error)}
    </p>
  );
}
function Loading() {
  return (
    <p className="notice" role="status">
      Loading…
    </p>
  );
}
function Empty({ children }: { children: ReactNode }) {
  return <div className="empty">{children}</div>;
}
export default function App() {
  const [session, setSession] = useState<Session | null>(null);
  const client = useQueryClient();
  function logout() {
    delete api.defaults.headers.common.Authorization;
    setSession(null);
    client.clear();
  }
  function login(s: Session) {
    client.clear();
    api.defaults.headers.common.Authorization = `Bearer ${s.token}`;
    setSession(s);
  }
  useEffect(() => {
    if (!session) return;
    const timer = setTimeout(
      logout,
      Math.max(0, session.expiresAt * 1000 - Date.now()),
    );
    return () => clearTimeout(timer);
  }, [session]);
  return (
    <Auth.Provider value={{ session, login }}>
      <div className="shell">
        <header>
          <Link to="/" className="brand">
            <span className="brand-mark">
              C<span>i</span>
            </span>{' '}
            Commerce<span className="brand-light">Intelligence</span>
          </Link>
          <nav aria-label="Main navigation">
            <NavLink to="/" end>
              Discover
            </NavLink>
            {session && (
              <>
                <NavLink to="/cart">Cart</NavLink>
                <NavLink to="/orders">Orders</NavLink>
              </>
            )}
            {session?.role === 'ADMIN' && (
              <NavLink to="/admin">Workspace</NavLink>
            )}
            {session ? (
              <button className="text-button" onClick={logout}>
                Sign out
              </button>
            ) : (
              <NavLink to="/login">Sign in</NavLink>
            )}
          </nav>
        </header>
        <main>
          <Routes>
            <Route path="/" element={<Catalog />} />
            <Route path="/products/:id" element={<ProductDetail />} />
            <Route path="/login" element={<Login />} />
            <Route
              path="/cart"
              element={session ? <Cart /> : <Navigate to="/login" replace />}
            />
            <Route
              path="/orders"
              element={session ? <Orders /> : <Navigate to="/login" replace />}
            />
            <Route
              path="/admin"
              element={
                session?.role === 'ADMIN' ? (
                  <Admin />
                ) : (
                  <Navigate to="/" replace />
                )
              }
            />
            <Route
              path="*"
              element={
                <Empty>
                  Page not found. <Link to="/">Browse products</Link>
                </Empty>
              }
            />
          </Routes>
        </main>
        <footer>
          <span>Commerce Intelligence</span>
          <span>Portfolio application · Simulated payments · INR (₹)</span>
        </footer>
      </div>
    </Auth.Provider>
  );
}
function ProductImage({ src, name, className = '' }: { src?: string; name: string; className?: string }) {
  return <img className={`product-image ${className}`} src={src || '/images/products/placeholder.svg'}
    alt={name} loading="lazy" width={600} height={440}
    onError={(event) => { if (!event.currentTarget.src.endsWith('/placeholder.svg')) event.currentTarget.src = '/images/products/placeholder.svg'; }} />;
}
function ProductCard({ p }: { p: Product }) {
  return (
    <Link className="product-card" to={`/products/${p.id}`}>
      <div className="product-art">
        <ProductImage src={p.imageUrl} name={p.name} />
        <small>{p.sku}</small>
      </div>
      <div className="product-meta">
        <h3>{p.name}</h3>
        <strong>{money(p.price)}</strong>
      </div>
      <p>{p.description}</p>
      <span className={p.stock ? 'availability' : 'sold-out'}>
        {p.stock ? `${p.stock} available` : 'Out of stock'}
      </span>
    </Link>
  );
}
export function Catalog() {
  const [params, setParams] = useSearchParams();
  const [search, setSearch] = useState(params.get('q') ?? '');
  const products = useQuery({
    queryKey: ['products', params.toString()],
    queryFn: async () =>
      (await api.get<Page<Product>>('/products', { params })).data,
  });
  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: async () => (await api.get<Category[]>('/categories')).data,
  });
  function change(key: string, value: string) {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    if (key !== 'page') next.delete('page');
    setParams(next);
  }
  return (
    <>
      <section className="page-heading">
        <div>
          <p className="eyebrow">THE COLLECTION</p>
          <h1>Find your next essential.</h1>
          <p>Browse the catalog. Choose with confidence.</p>
        </div>
        <span className="tag">Live inventory</span>
      </section>
      <section className="catalog-toolbar" aria-label="Product filters">
        <form
          className="search"
          onSubmit={(e) => {
            e.preventDefault();
            change('q', search);
          }}
        >
          <label className="sr-only" htmlFor="search">
            Search products
          </label>
          <input
            id="search"
            placeholder="Search products…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <button type="submit">Search</button>
        </form>
        <label>
          Category
          <select
            value={params.get('category') ?? ''}
            onChange={(e) => change('category', e.target.value)}
          >
            <option value="">All categories</option>
            {categories.data?.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Sort by
          <select
            value={params.get('sort') ?? 'name'}
            onChange={(e) => change('sort', e.target.value)}
          >
            <option value="name">Name</option>
            <option value="price-asc">Price: low to high</option>
            <option value="price-desc">Price: high to low</option>
          </select>
        </label>
        <label>
          Max price
          <input
            aria-label="Maximum price"
            type="number"
            min="0"
            step="0.01"
            placeholder="Any"
            value={params.get('max') ?? ''}
            onChange={(e) => change('max', e.target.value)}
          />
        </label>
      </section>
      {categories.isError && <ErrorBox error={categories.error} />}
      <div className="section-label">
        <h2>Products</h2>
        <span>{products.data?.totalElements ?? 0} results</span>
      </div>
      {products.isPending ? (
        <Loading />
      ) : products.isError ? (
        <ErrorBox error={products.error} />
      ) : products.data.content.length ? (
        <>
          <div className="product-grid">
            {products.data.content.map((p) => (
              <ProductCard key={p.id} p={p} />
            ))}
          </div>
          <div className="pagination">
            <button
              disabled={products.data.number === 0}
              onClick={() => change('page', String(products.data.number - 1))}
            >
              Previous
            </button>
            <span>
              Page {products.data.number + 1} of {products.data.totalPages}
            </span>
            <button
              disabled={products.data.number + 1 >= products.data.totalPages}
              onClick={() => change('page', String(products.data.number + 1))}
            >
              Next
            </button>
          </div>
        </>
      ) : (
        <Empty>
          <h3>No products found</h3>
          <p>
            Try another search or category. Administrators can add products in
            the workspace.
          </p>
        </Empty>
      )}
    </>
  );
}
function ProductDetail() {
  const { id } = useParams();
  const { session } = useContext(Auth);
  const client = useQueryClient();
  const product = useQuery({
    queryKey: ['product', id],
    queryFn: async () => (await api.get<Product>(`/products/${id}`)).data,
  });
  const add = useMutation({
    mutationFn: () => api.post(`/cart/${id}`),
    onSuccess: () => client.invalidateQueries({ queryKey: ['cart'] }),
  });
  const recommendations = useQuery({
    queryKey: ['recommendations', id],
    queryFn: async () =>
      (
        await api.get<{
          status: string;
          reason?: string;
          recommendations: { id: string; name: string; score: number }[];
        }>(`/products/${id}/recommendations`)
      ).data,
  });
  if (product.isPending) return <Loading />;
  if (product.isError) return <ErrorBox error={product.error} />;
  const p = product.data;
  return (
    <>
      <Link className="back" to="/">
        ← Collection
      </Link>
      <div className="detail">
        <div className="product-art large">
          <ProductImage src={p.imageUrl} name={p.name} />
        </div>
        <div>
          <p className="eyebrow">{p.sku}</p>
          <h1>{p.name}</h1>
          <p className="price">{money(p.price)}</p>
          <p>{p.description}</p>
          <p>{p.stock} units in stock</p>
          {session ? (
            <button
              disabled={!p.stock || add.isPending}
              onClick={() => add.mutate()}
            >
              Add one to cart
            </button>
          ) : (
            <Link className="button" to="/login">
              Sign in to shop
            </Link>
          )}
          {add.isError && <ErrorBox error={add.error} />}
          {add.isSuccess && (
            <p role="status">
              Cart updated. <Link to="/cart">View cart</Link>
            </p>
          )}
        </div>
      </div>
      <section className="panel">
        <h2>Related products</h2>
        <p className="muted">
          Content similarity based on product descriptions and categories.
        </p>
        {recommendations.isPending ? (
          <Loading />
        ) : recommendations.isError ? (
          <p>Recommendations are currently unavailable.</p>
        ) : recommendations.data.status !== 'ready' ? (
          <p>{recommendations.data.reason}</p>
        ) : (
          <div className="related">
            {recommendations.data.recommendations.map((p) => (
              <Link key={p.id} to={`/products/${p.id}`}>
                {p.name}
              </Link>
            ))}
          </div>
        )}
      </section>
    </>
  );
}
function Login() {
  const { session, login } = useContext(Auth);
  const [register, setRegister] = useState(false);
  const navigate = useNavigate();
  const action = useMutation({
    mutationFn: async (data: { email: string; password: string }) =>
      (
        await api.post<Session>(
          `/auth/${register ? 'register' : 'login'}`,
          data,
        )
      ).data,
    onSuccess: (s) => {
      login(s);
      navigate('/');
    },
  });
  if (session) return <Navigate to="/" />;
  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    action.mutate({
      email: String(f.get('email')),
      password: String(f.get('password')),
    });
  }
  return (
    <section className="auth panel">
      <p className="eyebrow">YOUR ACCOUNT</p>
      <h1>{register ? 'Make yourself at home.' : 'Welcome back.'}</h1>
      <p>Sign in to manage your cart and orders.</p>
      <form onSubmit={submit}>
        <label>
          Email
          <input
            name="email"
            type="email"
            required
            maxLength={254}
            autoComplete="email"
          />
        </label>
        <label>
          Password
          <input
            name="password"
            type="password"
            required
            minLength={12}
            maxLength={72}
            autoComplete={register ? 'new-password' : 'current-password'}
          />
        </label>
        <small>12–72 characters. Sessions last 15 minutes.</small>
        <button disabled={action.isPending}>
          {action.isPending
            ? 'Please wait…'
            : register
              ? 'Create account'
              : 'Sign in'}
        </button>
      </form>
      {action.isError && <ErrorBox error={action.error} />}
      <button
        className="text-button"
        onClick={() => {
          setRegister(!register);
          action.reset();
        }}
      >
        {register
          ? 'Already have an account? Sign in'
          : 'New here? Create an account'}
      </button>
    </section>
  );
}
type CartLine = {
  imageUrl?: string;
  productId: string;
  name: string;
  price: number;
  quantity: number;
  stock: number;
};
type Order = {
  id: string;
  status: string;
  total: number;
  createdAt: string;
  items: {
    id: string;
    productName: string;
    imageUrl?: string;
    quantity: number;
    unitPrice: number;
  }[];
};
function Cart() {
  const client = useQueryClient();
  const key = useRef(crypto.randomUUID());
  const [decline, setDecline] = useState(false);
  const [result, setResult] = useState<Order | null>(null);
  const cart = useQuery({
    queryKey: ['cart'],
    queryFn: async () => (await api.get<CartLine[]>('/cart')).data,
  });
  const update = useMutation({
    mutationFn: ({ id, quantity }: { id: string; quantity: number }) =>
      api.put(`/cart/${id}`, { quantity }),
    onSuccess: () => {
      key.current = crypto.randomUUID();
      client.invalidateQueries({ queryKey: ['cart'] });
    },
  });
  const checkout = useMutation({
    mutationFn: async () =>
      (
        await api.post<Order>(
          '/checkout',
          { simulateDecline: decline },
          { headers: { 'Idempotency-Key': key.current } },
        )
      ).data,
    onSuccess: (o) => {
      setResult(o);
      key.current = crypto.randomUUID();
      client.invalidateQueries({ queryKey: ['cart'] });
      client.invalidateQueries({ queryKey: ['orders'] });
      client.invalidateQueries({ queryKey: ['products'] });
    },
  });
  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">YOUR SELECTION</p>
          <h1>Shopping cart</h1>
        </div>
        <Link to="/">Continue shopping</Link>
      </div>
      {cart.isPending ? (
        <Loading />
      ) : cart.isError ? (
        <ErrorBox error={cart.error} />
      ) : (
        <div className="cart-layout">
          <section className="panel">
            {cart.data.length ? (
              cart.data.map((l) => (
                <div className="cart-line" key={l.productId}>
                  <div className="cart-product">
                    <ProductImage src={l.imageUrl} name={l.name} className="thumbnail" />
                    <div>
                    <Link to={`/products/${l.productId}`}>{l.name}</Link>
                    <p>{money(l.price)} each</p>
                    {l.quantity > l.stock && <p role="alert">Insufficient stock or unavailable. Remove this item or reduce its quantity.</p>}
                    <button disabled={update.isPending || checkout.isPending} onClick={() => update.mutate({ id: l.productId, quantity: 0 })}>Remove {l.name}</button>
                    </div>
                  </div>
                  <div className="quantity">
                    <button
                      aria-label={`Decrease ${l.name}`}
                      disabled={update.isPending || checkout.isPending}
                      onClick={() =>
                        update.mutate({
                          id: l.productId,
                          quantity: l.quantity - 1,
                        })
                      }
                    >
                      −
                    </button>
                    <span>{l.quantity}</span>
                    <button
                      aria-label={`Increase ${l.name}`}
                      disabled={
                        update.isPending ||
                        checkout.isPending ||
                        l.quantity >= Math.min(l.stock, 100)
                      }
                      onClick={() =>
                        update.mutate({
                          id: l.productId,
                          quantity: l.quantity + 1,
                        })
                      }
                    >
                      +
                    </button>
                  </div>
                  <strong>{money(l.price * l.quantity)}</strong>
                </div>
              ))
            ) : (
              <Empty>Your cart is empty.</Empty>
            )}
          </section>
          <aside className="panel">
            <h2>Order summary</h2>
            <div className="total">
              <span>Total</span>
              <strong>
                {money(cart.data.reduce((a, l) => a + l.price * l.quantity, 0))}
              </strong>
            </div>
            <p>Simulated payment only. No payment information is collected.</p>
            <label className="checkbox">
              <input
                type="checkbox"
                checked={decline}
                disabled={checkout.isPending}
                onChange={(e) => {
                  setDecline(e.target.checked);
                  key.current = crypto.randomUUID();
                }}
              />
              Test a declined payment
            </label>
            <button
              disabled={
                !cart.data.length || cart.data.some(l => l.quantity > l.stock) || checkout.isPending || update.isPending
              }
              onClick={() => checkout.mutate()}
            >
              {checkout.isPending ? 'Processing…' : 'Place simulated order'}
            </button>
          </aside>
        </div>
      )}
      {update.isError && <ErrorBox error={update.error} />}
      {checkout.isError && <ErrorBox error={checkout.error} />}
      {result && (
        <div role="status" className="notice">
          {result.status === 'PAID'
            ? 'Order confirmed.'
            : 'Payment declined. Your cart and inventory are unchanged.'}{' '}
          <Link to="/orders">View orders</Link>
        </div>
      )}
    </>
  );
}
function Orders() {
  const orders = useQuery({
    queryKey: ['orders'],
    queryFn: async () => (await api.get<Order[]>('/orders')).data,
  });
  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">YOUR PURCHASES</p>
          <h1>Order history</h1>
        </div>
      </div>
      {orders.isPending ? (
        <Loading />
      ) : orders.isError ? (
        <ErrorBox error={orders.error} />
      ) : !orders.data.length ? (
        <Empty>No orders yet.</Empty>
      ) : (
        orders.data.map((o) => (
          <article className="panel order" key={o.id}>
            <div className="section-label">
              <h2>{new Date(o.createdAt).toLocaleString()}</h2>
              <span className="tag">{o.status.replaceAll('_', ' ')}</span>
            </div>
            <small>{o.id}</small>
            {o.items.map((i) => (
              <p key={i.id} className="order-product">
                <ProductImage src={i.imageUrl} name={i.productName} className="thumbnail" />
                <span>{i.productName} × {i.quantity}</span>
                <strong>{money(i.unitPrice * i.quantity)}</strong>
              </p>
            ))}
            <div className="total">
              Total<strong>{money(o.total)}</strong>
            </div>
          </article>
        ))
      )}
    </>
  );
}
type Analytics = {
  summary: { orders: number; revenue: number; average_order_value: number };
  daily: { date: string; orders: number; revenue: number }[];
  topProducts: {
    product_id: string;
    name: string;
    units: number;
    revenue: number;
  }[];
};
type Insight = {
  status: string;
  reason?: string;
  method?: string;
  trainingRows?: number;
  forecast?: { date: string; units: number }[];
  anomalies?: { date: string; revenue: number; score: number }[];
  validationMae?: number;
};
function Admin() {
  const client = useQueryClient();
  const [tab, setTab] = useState('overview');
  const [orderPage, setOrderPage] = useState(0);
  const [auditId, setAuditId] = useState('');
  const [auditPage, setAuditPage] = useState(0);
  const adminOrders = useQuery({ queryKey: ['admin-orders', orderPage], queryFn: async () => (await api.get<Page<Order>>(`/admin/orders?page=${orderPage}`)).data, enabled: tab === 'orders' });
  const audit = useQuery({ queryKey: ['inventory-audit', auditId, auditPage], queryFn: async () => (await api.get<Page<{id: string; delta: number; reason: string; createdAt: string}>>(`/admin/inventory/${auditId}/movements?page=${auditPage}`)).data, enabled: tab === 'inventory' && !!auditId });
  const [editing, setEditing] = useState<Product | null>(null);
  const saveProduct = useMutation({
    mutationFn: (p: Product) => api.put(`/admin/products/${p.id}`, p),
    onSuccess: () => { setEditing(null); client.invalidateQueries({ queryKey: ['inventory'] }); client.invalidateQueries({ queryKey: ['products'] }); client.invalidateQueries({ queryKey: ['cart'] }); },
  });
  const [productId, setProductId] = useState('');
  const analytics = useQuery({
    queryKey: ['analytics'],
    queryFn: async () => (await api.get<Analytics>('/admin/analytics')).data,
  });
  const stock = useQuery({
    queryKey: ['inventory'],
    queryFn: async () => (await api.get<Product[]>('/admin/inventory')).data,
  });
  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: async () => (await api.get<Category[]>('/categories')).data,
  });
  const forecast = useQuery({
    queryKey: ['forecast', productId],
    queryFn: async () =>
      (await api.get<Insight>(`/admin/intelligence/forecast/${productId}`))
        .data,
    enabled: tab === 'intelligence' && !!productId,
  });
  const anomalies = useQuery({
    queryKey: ['anomalies'],
    queryFn: async () =>
      (await api.get<Insight>('/admin/intelligence/anomalies')).data,
    enabled: tab === 'intelligence',
  });
  const action = useMutation({
    mutationFn: ({ url, data }: { url: string; data: unknown }) =>
      api.post(url, data),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ['inventory'] });
      client.invalidateQueries({ queryKey: ['inventory-audit'] });
      client.invalidateQueries({ queryKey: ['categories'] });
      client.invalidateQueries({ queryKey: ['products'] });
    },
  });
  function form(e: FormEvent<HTMLFormElement>, kind: string) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    const data = Object.fromEntries(f);
    if (kind === 'product')
      action.mutate({
        url: '/admin/products',
        data: { ...data, price: Number(data.price), stock: Number(data.stock) },
      });
    else if (kind === 'category')
      action.mutate({ url: '/admin/categories', data });
    else
      action.mutate({
        url: `/admin/inventory/${data.id}/adjustments`,
        data: { delta: Number(data.delta), reason: data.reason },
      });
  }
  return (
    <>
      <section className="page-heading">
        <div>
          <p className="eyebrow">OPERATIONS WORKSPACE</p>
          <h1>Commerce, in focus.</h1>
          <p>Sales, inventory, and signals from your actual business data.</p>
        </div>
        <span className="tag">Administrator</span>
      </section>
      <div className="tabs">
        {['overview', 'inventory', 'orders', 'intelligence'].map((t) => (
          <button
            key={t}
            className={tab === t ? 'active' : ''}
            onClick={() => setTab(t)}
          >
            {t[0].toUpperCase() + t.slice(1)}
          </button>
        ))}
      </div>
      {tab === 'orders' && <section className="panel">
        <h2>All orders</h2>
        <p>Payments are simulated. Amounts are stored order snapshots in INR.</p>
        {adminOrders.isPending ? <Loading /> : adminOrders.isError ? <ErrorBox error={adminOrders.error} /> : <>
          {adminOrders.data.content.map(o => <article className="panel" key={o.id}>
            <h3>Order {o.id}</h3><p>{new Date(o.createdAt).toLocaleString()} · {o.status} · {money(o.total)}</p>
            {o.items.map(i => <div className="cart-product" key={i.id}><ProductImage src={i.imageUrl} name={i.productName} className="thumbnail" /><span>{i.productName} × {i.quantity} · {money(i.unitPrice * i.quantity)}</span></div>)}
          </article>)}
          {!adminOrders.data.totalElements && <Empty>No orders yet.</Empty>}
          <button disabled={!orderPage} onClick={() => setOrderPage(p => p - 1)}>Previous orders</button>
          <span> Page {orderPage + 1} </span>
          <button disabled={orderPage + 1 >= adminOrders.data.totalPages} onClick={() => setOrderPage(p => p + 1)}>Next orders</button>
        </>}
      </section>}
      {tab === 'overview' &&
        (analytics.isPending ? (
          <Loading />
        ) : analytics.isError ? (
          <ErrorBox error={analytics.error} />
        ) : (
          <>
            <div className="metrics">
              <div>
                <span>Revenue · all time</span>
                <strong>{money(analytics.data.summary.revenue)}</strong>
              </div>
              <div>
                <span>Paid orders</span>
                <strong>{analytics.data.summary.orders}</strong>
              </div>
              <div>
                <span>Average order value</span>
                <strong>
                  {money(analytics.data.summary.average_order_value)}
                </strong>
              </div>
            </div>
            <div className="admin-grid">
              <section className="panel">
                <h2>Sales · last 30 days</h2>
                <p className="muted">UTC · Paid orders only</p>
                {!analytics.data.daily.length ? (
                  <Empty>No paid orders to chart yet.</Empty>
                ) : (
                  <div className="bar-chart">
                    {analytics.data.daily.map((d) => (
                      <div className="bar-row" key={d.date}>
                        <span>{d.date}</span>
                        <div>
                          <i
                            style={{
                              width: `${(Number(d.revenue) / Math.max(...analytics.data.daily.map((x) => Number(x.revenue)), 1)) * 100}%`,
                            }}
                          />
                        </div>
                        <strong>{money(d.revenue)}</strong>
                      </div>
                    ))}
                  </div>
                )}
              </section>
              <section className="panel">
                <h2>Top products · all time</h2>
                {!analytics.data.topProducts.length ? (
                  <Empty>No sales yet.</Empty>
                ) : (
                  analytics.data.topProducts.map((p) => (
                    <div className="rank" key={p.product_id}>
                      <span>
                        {p.name}
                        <small>{p.units} units</small>
                      </span>
                      <strong>{money(p.revenue)}</strong>
                    </div>
                  ))
                )}
              </section>
            </div>
          </>
        ))}
      {tab === 'inventory' && (
        <>
          {stock.isPending ? (
            <Loading />
          ) : stock.isError ? (
            <ErrorBox error={stock.error} />
          ) : (
            <div className="panel table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Product</th>
                    <th>SKU</th>
                    <th>Price</th>
                    <th>Stock / status</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {stock.data.map((p) => (
                    <tr key={p.id}>
                      <td><div className="admin-product"><ProductImage src={p.imageUrl} name={p.name} className="thumbnail" /><span>{p.name}</span></div></td>
                      <td>{p.sku}</td>
                      <td>{money(p.price)}</td>
                      <td>{p.stock} · {p.active ? 'Active' : 'Archived'}</td>
                      <td><button aria-label={`Edit ${p.name}`} onClick={() => { saveProduct.reset(); setEditing(p); }}>Edit</button> <button aria-label={`History ${p.name}`} onClick={() => { setAuditId(p.id); setAuditPage(0); }}>History</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {!stock.data.length && (
                <Empty>No products. Add your first product below.</Empty>
              )}
            </div>
          )}
          {auditId && <section className="panel">
            <h2>Inventory history · {stock.data?.find(p => p.id === auditId)?.name}</h2>
            {audit.isPending ? <Loading /> : audit.isError ? <ErrorBox error={audit.error} /> : <>
              {audit.data.content.map(m => <p key={m.id}>{new Date(m.createdAt).toLocaleString()} · {m.delta > 0 ? '+' : ''}{m.delta} · {m.reason}</p>)}
              {!audit.data.totalElements && <Empty>No inventory movements yet.</Empty>}
              <button disabled={!auditPage} onClick={() => setAuditPage(p => p - 1)}>Previous movements</button>
              <span> Page {auditPage + 1} </span>
              <button disabled={auditPage + 1 >= audit.data.totalPages} onClick={() => setAuditPage(p => p + 1)}>Next movements</button>
            </>}
          </section>}
          {editing && <section className="panel">
            <h2>Edit product</h2>
            <form onSubmit={(e) => { e.preventDefault(); saveProduct.mutate(editing); }}>
              <label>Name<input autoFocus required maxLength={200} value={editing.name} onChange={e => setEditing({...editing, name: e.target.value})} /></label>
              <label>Description<textarea required maxLength={4000} value={editing.description} onChange={e => setEditing({...editing, description: e.target.value})} /></label>
              <label>Category<select value={editing.categoryId} onChange={e => setEditing({...editing, categoryId: e.target.value})}>{categories.data?.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select></label>
              <label>Price (₹)<input type="number" required min="0" max="9999999999.99" step="0.01" value={editing.price} onChange={e => setEditing({...editing, price: Number(e.target.value)})} /></label>
              <label><input type="checkbox" checked={editing.active} onChange={e => setEditing({...editing, active: e.target.checked})} />Available in catalog</label>
              {saveProduct.isError && <ErrorBox error={saveProduct.error} />}
              <button disabled={saveProduct.isPending}>Save product</button>
              <button type="button" disabled={saveProduct.isPending} onClick={() => setEditing(null)}>Cancel</button>
            </form>
          </section>}
          <div className="admin-grid forms">
            <section className="panel">
              <h2>Add product</h2>
              <form onSubmit={(e) => form(e, 'product')}>
                <label>
                  Name
                  <input name="name" required maxLength={200} />
                </label>
                <label>
                  SKU
                  <input name="sku" required maxLength={64} />
                </label>
                <label>
                  Description
                  <textarea name="description" required maxLength={4000} />
                </label>
                <label>
                  Category
                  <select name="categoryId" required>
                    <option value="">Choose a category</option>
                    {categories.data?.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                  </select>
                </label>
                <div className="two-fields">
                  <label>
                    Price (₹)
                    <input
                      name="price"
                      type="number"
                      min="0"
                      step="0.01"
                      required
                    />
                  </label>
                  <label>
                    Initial stock
                    <input
                      name="stock"
                      type="number"
                      min="0"
                      step="1"
                      required
                    />
                  </label>
                </div>
                <button disabled={action.isPending}>Create product</button>
              </form>
            </section>
            <div>
              <section className="panel">
                <h2>Add category</h2>
                <form onSubmit={(e) => form(e, 'category')}>
                  <label>
                    Category name
                    <input name="name" required maxLength={100} />
                  </label>
                  <button disabled={action.isPending}>Create category</button>
                </form>
              </section>
              <section className="panel">
                <h2>Adjust inventory</h2>
                <form onSubmit={(e) => form(e, 'adjust')}>
                  <label>
                    Product
                    <select name="id" required>
                      <option value="">Choose a product</option>
                      {stock.data?.map((p) => (
                        <option key={p.id} value={p.id}>
                          {p.name}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    Quantity change
                    <input
                      name="delta"
                      type="number"
                      min="-1000000"
                      max="1000000"
                      step="1"
                      required
                      placeholder="e.g. 10 or -2"
                    />
                  </label>
                  <label>
                    Reason
                    <input name="reason" required maxLength={300} />
                  </label>
                  <button disabled={action.isPending}>Save adjustment</button>
                </form>
              </section>
            </div>
          </div>
          {action.isError && <ErrorBox error={action.error} />}
          {action.isSuccess && (
            <p className="notice" role="status">
              Saved successfully.
            </p>
          )}
        </>
      )}
      {tab === 'intelligence' && (
        <div className="admin-grid">
          <section className="panel">
            <h2>Demand forecast</h2>
            <p>
              Trained on daily paid order quantities. Requires at least 35 days
              of history and 7 sale days.
            </p>
            <label>
              Product
              <select
                value={productId}
                onChange={(e) => setProductId(e.target.value)}
              >
                <option value="">Choose a product</option>
                {stock.data?.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
            </label>
            {productId &&
              (forecast.isPending ? (
                <Loading />
              ) : forecast.isError ? (
                <ErrorBox error={forecast.error} />
              ) : (
                <InsightView value={forecast.data} />
              ))}
          </section>
          <section className="panel">
            <h2>Revenue anomaly detection</h2>
            <p>
              Isolation Forest evaluates daily revenue. Statistical flags are
              signals for review, not proof of an issue.
            </p>
            {anomalies.isPending ? (
              <Loading />
            ) : anomalies.isError ? (
              <ErrorBox error={anomalies.error} />
            ) : (
              <InsightView value={anomalies.data} />
            )}
          </section>
        </div>
      )}
    </>
  );
}
function InsightView({ value }: { value: Insight }) {
  if (value.status !== 'ready')
    return (
      <Empty>
        {value.reason ?? 'Not enough data to produce a reliable result.'}
      </Empty>
    );
  return (
    <>
      <p className="muted">
        {value.method} · {value.trainingRows} training rows
        {value.validationMae !== undefined &&
          ` · Holdout MAE ${value.validationMae.toFixed(2)} units`}
      </p>
      {value.forecast?.map((x) => (
        <div className="rank" key={x.date}>
          <span>{x.date}</span>
          <strong>{x.units.toFixed(1)} units</strong>
        </div>
      ))}
      {value.anomalies?.length === 0 && (
        <p>No anomalies detected in the evaluated period.</p>
      )}
      {value.anomalies?.map((x) => (
        <div className="rank" key={x.date}>
          <span>{x.date}</span>
          <strong>{money(x.revenue)}</strong>
        </div>
      ))}
    </>
  );
}
