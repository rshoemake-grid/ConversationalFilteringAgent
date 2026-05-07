import { memo, useState, useRef, useLayoutEffect } from 'react';
import { createPortal } from 'react-dom';
import type { ProductDto } from '../api/types';

interface ProductCardProps {
  product: ProductDto;
  index: number;
}

function formatValue(v: unknown): string {
  if (v == null) return '—';
  if (typeof v === 'boolean') return v ? 'Yes' : 'No';
  if (typeof v === 'object') return JSON.stringify(v);
  return String(v);
}

function ProductCardComponent({ product: p, index }: ProductCardProps) {
  const hasImage = !!p.imageUri;
  const [isHovered, setIsHovered] = useState(false);
  const [anchorRect, setAnchorRect] = useState<DOMRect | null>(null);
  const anchorRef = useRef<HTMLDivElement>(null);

  useLayoutEffect(() => {
    if (!isHovered || !anchorRef.current) {
      setAnchorRect(null);
      return;
    }
    const el = anchorRef.current;
    const update = () => setAnchorRect(el.getBoundingClientRect());
    update();
    if (typeof ResizeObserver !== 'undefined') {
      const ob = new ResizeObserver(update);
      ob.observe(el);
      window.addEventListener('scroll', update, true);
      return () => {
        ob.disconnect();
        window.removeEventListener('scroll', update, true);
      };
    }
    window.addEventListener('scroll', update, true);
    return () => window.removeEventListener('scroll', update, true);
  }, [isHovered]);

  const hoverTarget = (
    <div
      ref={anchorRef}
      className={`product-card__hover-target ${hasImage ? 'product-card__hover-target--has-image' : ''}`}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
    >
      {hasImage ? (
        <img src={p.imageUri} alt={p.title} className="product-card__image" />
      ) : (
        <div className="product-card__image-placeholder" aria-hidden>Product</div>
      )}
      {isHovered &&
        anchorRect &&
        createPortal(
          <div
            className="product-card__detail-popover product-card__detail-popover--portal"
            role="tooltip"
            style={{
              position: 'fixed',
              left: anchorRect.left + anchorRect.width / 2,
              bottom: window.innerHeight - anchorRect.top + 8,
              transform: 'translateX(-50%)',
            }}
            onMouseEnter={() => setIsHovered(true)}
            onMouseLeave={() => setIsHovered(false)}
          >
            <div className="product-card__detail-header">
              {p.title || 'Product'}
              {p.detailsFetched && (
                <span className="product-card__detail-fetched" title="Details were looked up from product catalog">
                  !
                </span>
              )}
            </div>
            <dl className="product-card__detail-body">
          {p.description != null && (
            <>
              <dt>Description</dt>
              <dd>{p.description}</dd>
            </>
          )}
          {p.price != null && (
            <>
              <dt>Price</dt>
              <dd>{p.price}</dd>
            </>
          )}
          {p.gtin != null && (
            <>
              <dt>UPC / GTIN</dt>
              <dd>{p.gtin}</dd>
            </>
          )}
          {p.productId != null && (
            <>
              <dt>Product ID</dt>
              <dd>{p.productId}</dd>
            </>
          )}
          {p.brands?.length ? (
            <>
              <dt>Brands</dt>
              <dd>{p.brands.join(', ')}</dd>
            </>
          ) : null}
          {p.categories?.length ? (
            <>
              <dt>Categories</dt>
              <dd>{p.categories.join(' › ')}</dd>
            </>
          ) : null}
          {p.availability != null && (
            <>
              <dt>Availability</dt>
              <dd>{p.availability.replace(/_/g, ' ')}</dd>
            </>
          )}
          {p.sizes?.length ? (
            <>
              <dt>Sizes</dt>
              <dd>{p.sizes.join(', ')}</dd>
            </>
          ) : null}
          {p.materials?.length ? (
            <>
              <dt>Materials</dt>
              <dd>{p.materials.join(', ')}</dd>
            </>
          ) : null}
          {p.uri != null && (
            <>
              <dt>Link</dt>
              <dd>
                <a href={p.uri} target="_blank" rel="noopener noreferrer" className="product-card__detail-link">
                  {p.uri}
                </a>
              </dd>
            </>
          )}
          {p.attributes && Object.keys(p.attributes).length > 0 && (
            <>
              <dt>Attributes</dt>
              <dd>
                <ul className="product-card__detail-attributes">
                  {Object.entries(p.attributes).map(([k, v]) => (
                    <li key={k}>
                      <span className="product-card__detail-attr-key">{k}:</span>{' '}
                      {formatValue(v)}
                    </li>
                  ))}
                </ul>
              </dd>
            </>
          )}
            </dl>
          </div>,
          document.body
        )}
    </div>
  );

  return (
    <div key={p.id || `p-${index}`} className="product-card">
      {hoverTarget}
      <div className="product-card__title">{p.title}</div>
      {p.description && (
        <div className="product-card__desc">{p.description}</div>
      )}
      {p.price && (
        <div className="product-card__price">{p.price}</div>
      )}
    </div>
  );
}

export const ProductCard = memo(ProductCardComponent);
