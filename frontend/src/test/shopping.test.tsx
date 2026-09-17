import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import App from '../App';
import { api } from '../api';

vi.mock('../api', async () => ({
  ...(await vi.importActual<typeof import('../api')>('../api')),
  api: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    defaults: { headers: { common: {} } },
  },
}));
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});

const product = {
  id: 'p1',
  sku: 'TEST',
  name: 'Test headphones',
  description: 'Wireless audio',
  imageUrl: '/images/products/headphones.svg',
  price: 12.5,
  stock: 5,
  categoryId: 'c1',
  active: true,
  version: 0,
};

async function signIn(role: string) {
  vi.mocked(api.get).mockImplementation(async (url) => ({
    data:
      url === '/categories'
        ? []
        : url === '/products/p1'
          ? product
          : url.endsWith('/recommendations')
            ? { status: 'ready', recommendations: [] }
            : {
                content: [product],
                totalElements: 1,
                totalPages: 1,
                number: 0,
              },
  }));
  vi.mocked(api.post).mockResolvedValue({
    data: {
      token: 'test-token',
      email: 'test@example.test',
      role,
      expiresAt: Math.floor(Date.now() / 1000) + 900,
    },
  });
  render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <MemoryRouter initialEntries={['/login']}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  await userEvent.type(screen.getByLabelText('Email'), 'test@example.test');
  await userEvent.type(
    screen.getByLabelText('Password'),
    'testing-password-123',
  );
  await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
  await screen.findByRole('link', { name: /Test headphones/ });
}

describe('authenticated shopping', () => {
  it('adds through the atomic endpoint and clears the session on logout', async () => {
    await signIn('CUSTOMER');
    expect(
      screen.queryByRole('link', { name: 'Workspace' }),
    ).not.toBeInTheDocument();
    await userEvent.click(
      await screen.findByRole('link', { name: /Test headphones/ }),
    );
    await userEvent.click(
      await screen.findByRole('button', { name: 'Detail close-up' }),
    );
    expect(screen.getByRole('img', { name: product.name })).toHaveAttribute(
      'src',
      '/images/products/headphones-detail.svg',
    );
    expect(screen.queryByText('TEST')).not.toBeInTheDocument();
    const add = await screen.findByRole('button', { name: 'Add one to cart' });
    await userEvent.click(add);
    await screen.findByText('Cart updated.');
    await userEvent.click(add);
    await waitFor(() =>
      expect(
        vi.mocked(api.post).mock.calls.filter(([url]) => url === '/cart/p1'),
      ).toHaveLength(2),
    );
    expect(vi.mocked(api.get).mock.calls.some(([url]) => url === '/cart')).toBe(
      false,
    );
    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }));
    expect(screen.getByRole('link', { name: 'Sign in' })).toBeInTheDocument();
    expect(api.defaults.headers.common.Authorization).toBeUndefined();
  });

  it('exposes the admin workspace for an admin session', async () => {
    await signIn('ADMIN');
    expect(screen.getByRole('link', { name: 'Workspace' })).toHaveAttribute(
      'href',
      '/admin',
    );
  });
  it('keeps an unavailable cart item removable and blocks checkout', async () => {
    await signIn('CUSTOMER');
    vi.mocked(api.get).mockImplementation(async (url) => ({
      data:
        url === '/cart'
          ? [
              {
                productId: 'p1',
                name: product.name,
                price: 1499,
                quantity: 2,
                stock: 0,
              },
            ]
          : url === '/categories'
            ? []
            : {
                content: [product],
                totalElements: 1,
                totalPages: 1,
                number: 0,
              },
    }));
    vi.mocked(api.put).mockResolvedValue({ data: [] });
    await userEvent.click(screen.getByRole('link', { name: 'Cart' }));
    expect(
      await screen.findByText(/Insufficient stock or unavailable/),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Place simulated order' }),
    ).toBeDisabled();
    await userEvent.click(
      screen.getByRole('button', { name: 'Remove Test headphones' }),
    );
    expect(api.put).toHaveBeenCalledWith('/cart/p1', { quantity: 0 });
  });

  it('sends the loaded version when an admin edits a product', async () => {
    await signIn('ADMIN');
    vi.mocked(api.get).mockImplementation(async (url) => ({
      data:
        url === '/admin/inventory'
          ? [product]
          : url === '/admin/categories'
            ? []
            : url === '/categories'
              ? [{ id: 'c1', name: 'Audio' }]
              : {
                  summary: { revenue: 0, orders: 0, average_order_value: 0 },
                  daily: [],
                  topProducts: [],
                },
    }));
    vi.mocked(api.put).mockResolvedValue({ data: { ...product, version: 1 } });
    await userEvent.click(screen.getByRole('link', { name: 'Workspace' }));
    await userEvent.click(screen.getByRole('button', { name: 'Inventory' }));
    await userEvent.click(
      await screen.findByRole('button', { name: 'Edit Test headphones' }),
    );
    await userEvent.selectOptions(
      screen.getAllByLabelText('Product image')[0],
      '/images/products/earbuds.svg',
    );
    await userEvent.click(screen.getByLabelText('Available in catalog'));
    await userEvent.click(screen.getByRole('button', { name: 'Save product' }));
    await waitFor(() =>
      expect(api.put).toHaveBeenCalledWith(
        '/admin/products/p1',
        expect.objectContaining({
          version: 0,
          active: false,
          imageUrl: '/images/products/earbuds.svg',
        }),
      ),
    );
  });

  it('loads and updates the authenticated profile', async () => {
    await signIn('CUSTOMER');
    vi.mocked(api.get).mockImplementation(async (url) => ({
      data:
        url === '/profile'
          ? {
              email: 'test@example.test',
              displayName: 'Asha',
              createdAt: '2026-01-01',
              purchases: { orders: 2, spent: 2998 },
            }
          : url === '/categories'
            ? []
            : {
                content: [product],
                totalElements: 1,
                totalPages: 1,
                number: 0,
              },
    }));
    vi.mocked(api.put).mockResolvedValue({ data: {} });
    await userEvent.click(screen.getByRole('link', { name: 'Account' }));
    const name = await screen.findByLabelText('Display name');
    await userEvent.clear(name);
    await userEvent.type(name, 'Asha Rao');
    await userEvent.click(screen.getByRole('button', { name: 'Save profile' }));
    await waitFor(() =>
      expect(api.put).toHaveBeenCalledWith('/profile', {
        displayName: 'Asha Rao',
      }),
    );
    expect(screen.getByText('2 successful orders')).toBeInTheDocument();
  });
});
