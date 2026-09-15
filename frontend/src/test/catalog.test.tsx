import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Catalog } from '../App';
import { api } from '../api';
vi.mock('../api', async () => {
  const actual = await vi.importActual<typeof import('../api')>('../api');
  return { ...actual, api: { get: vi.fn() } };
});
const product = {
  id: '1',
  sku: 'H-1',
  imageUrl: '/images/products/headphones.svg',
  name: 'Studio headphones',
  description: 'Wireless audio',
  price: 29.95,
  stock: 5,
  categoryId: 'c1',
  active: true,
};
function mount() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <Catalog />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
beforeEach(() => {
  cleanup();
  vi.resetAllMocks();
});
describe('catalog', () => {
  it('renders products returned by the API', async () => {
    vi.mocked(api.get).mockImplementation(async (url) => ({
      data:
        url === '/categories'
          ? []
          : { content: [product], totalElements: 1, totalPages: 1, number: 0 },
    }));
    mount();
    expect(await screen.findByText('Studio headphones')).toBeInTheDocument();
    expect(screen.getByText('₹29.95')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Studio headphones' })).toHaveAttribute('src', '/images/products/headphones.svg');
    expect(screen.getByText('5 available')).toBeInTheDocument();
  });
  it('shows an empty state without invented products', async () => {
    vi.mocked(api.get).mockResolvedValue({
      data: { content: [], totalElements: 0, totalPages: 0, number: 0 },
    });
    vi.mocked(api.get).mockImplementation(async (url) => ({
      data:
        url === '/categories'
          ? []
          : { content: [], totalElements: 0, totalPages: 0, number: 0 },
    }));
    mount();
    expect(await screen.findByText('No products found')).toBeInTheDocument();
  });
  it('submits search terms to the API', async () => {
    vi.mocked(api.get).mockImplementation(async (url) => ({
      data:
        url === '/categories'
          ? []
          : { content: [], totalElements: 0, totalPages: 0, number: 0 },
    }));
    mount();
    await userEvent.type(screen.getByLabelText('Search products'), 'speaker');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));
    await waitFor(() =>
      expect(
        vi
          .mocked(api.get)
          .mock.calls.some(
            ([url, config]) =>
              url === '/products' &&
              config?.params instanceof URLSearchParams &&
              config.params.get('q') === 'speaker',
          ),
      ).toBe(true),
    );
  });
  it('surfaces a failed catalog request', async () => {
    vi.mocked(api.get).mockRejectedValue(new Error('offline'));
    mount();
    expect((await screen.findAllByRole('alert')).length).toBeGreaterThan(0);
    expect(screen.queryByText('Studio headphones')).not.toBeInTheDocument();
  });
});
