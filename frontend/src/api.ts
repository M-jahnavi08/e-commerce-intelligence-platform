import axios from 'axios';
export const api = axios.create({ baseURL: '/api', timeout: 15000 });
export type Product = {
  id: string;
  sku: string;
  name: string;
  description: string;
  categoryId: string;
  price: number;
  imageUrl?: string;
  stock: number;
  active: boolean;
  version: number;
};
export type Category = { id: string; name: string };
export type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
};
export const money = (value: number) =>
  new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(
    value,
  );
export function errorMessage(e: unknown) {
  return axios.isAxiosError(e)
    ? (e.response?.data?.detail ??
        (e.response?.status === 401
          ? 'Please sign in again.'
          : 'Unable to complete the request. Please try again.'))
    : 'Something went wrong.';
}
