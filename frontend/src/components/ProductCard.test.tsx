import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProductCard } from './ProductCard';
import type { ProductDto } from '../api/types';

describe('ProductCard', () => {
  it('renders product title, description, and price', () => {
    const product: ProductDto = {
      id: 'p1',
      title: 'Test Product',
      description: 'A test product',
      price: '$19.99',
    };
    render(<ProductCard product={product} index={0} />);
    expect(screen.getAllByText('Test Product').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('A test product').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('$19.99').length).toBeGreaterThanOrEqual(1);
  });

  it('renders product image when imageUri is provided', () => {
    const product: ProductDto = {
      id: 'p2',
      title: 'Image Product',
      description: '',
      price: '$10',
      imageUri: 'https://example.com/product.png',
    };
    render(<ProductCard product={product} index={0} />);
    const img = screen.getByRole('img', { name: 'Image Product' });
    expect(img).toHaveAttribute('src', 'https://example.com/product.png');
  });

  it('shows popover with full product data on hover', async () => {
    const product: ProductDto = {
      id: 'projects/123/products/sku-456',
      title: 'Full Product',
      description: 'Full description',
      price: '$49.99',
      imageUri: 'https://example.com/img.png',
      gtin: '045496590417',
      productId: 'sku-456',
      brands: ['Nike'],
      categories: ['Shoes > Athletic'],
      availability: 'IN_STOCK',
      sizes: ['10', '11'],
      materials: ['Leather'],
      uri: 'https://store.com/product/123',
      attributes: { color: 'Black' },
    };
    render(<ProductCard product={product} index={0} />);
    const hoverTarget = screen.getByRole('img', { name: 'Full Product' }).closest('.product-card__hover-target');
    if (hoverTarget) await userEvent.hover(hoverTarget as HTMLElement);
    const tooltip = await screen.findByRole('tooltip');
    expect(tooltip).toBeInTheDocument();
    expect(tooltip).toHaveTextContent('Full Product');
    expect(tooltip).toHaveTextContent('045496590417');
    expect(tooltip).toHaveTextContent('sku-456');
    expect(tooltip).toHaveTextContent('Nike');
    expect(tooltip).toHaveTextContent('Shoes');
    expect(tooltip).toHaveTextContent('Athletic');
    expect(tooltip).toHaveTextContent('IN STOCK');
    expect(tooltip).toHaveTextContent('10, 11');
    expect(tooltip).toHaveTextContent('Leather');
    const link = screen.getByRole('link', { name: 'https://store.com/product/123' });
    expect(link).toHaveAttribute('href', 'https://store.com/product/123');
    expect(tooltip).toHaveTextContent('Black');
  });

  it('shows fetched indicator when detailsFetched is true', async () => {
    const product: ProductDto = {
      id: 'projects/p/products/enriched',
      title: 'Enriched Product',
      description: 'Looked up via Product.Get',
      price: '$12.99',
      imageUri: 'https://example.com/enriched.png',
      detailsFetched: true,
    };
    render(<ProductCard product={product} index={0} />);
    const hoverTarget = screen.getByRole('img', { name: 'Enriched Product' }).closest('.product-card__hover-target');
    if (hoverTarget) await userEvent.hover(hoverTarget as HTMLElement);
    const tooltip = await screen.findByRole('tooltip');
    expect(tooltip).toHaveTextContent('Enriched Product');
    expect(tooltip).toHaveTextContent('!');
    expect(screen.getByTitle('Details were looked up from product catalog')).toBeInTheDocument();
  });
});
