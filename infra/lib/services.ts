export interface ServiceDef {
  name: string;
  repoName: string;
  containerPort: number;
  pathPrefix: string;
  healthCheck: string;
  priority: number;
  desiredCount: number;
  cpu: number;
  memoryMiB: number;
}

export const SERVICES: ServiceDef[] = [
  { name: 'auth',         repoName: 'ecom/auth-service',         containerPort: 8081, pathPrefix: '/auth',          healthCheck: '/auth/health',          priority: 10, desiredCount: 1, cpu: 256, memoryMiB: 512 },
  { name: 'user',         repoName: 'ecom/user-service',         containerPort: 8082, pathPrefix: '/users',         healthCheck: '/users/health',         priority: 20, desiredCount: 1, cpu: 256, memoryMiB: 512 },
  { name: 'product',      repoName: 'ecom/product-service',      containerPort: 8083, pathPrefix: '/products',      healthCheck: '/products/health',      priority: 30, desiredCount: 1, cpu: 256, memoryMiB: 512 },
  { name: 'inventory',    repoName: 'ecom/inventory-service',    containerPort: 8084, pathPrefix: '/inventory',     healthCheck: '/inventory/health',     priority: 40, desiredCount: 1, cpu: 256, memoryMiB: 512 },
  { name: 'cart',         repoName: 'ecom/cart-service',         containerPort: 8085, pathPrefix: '/cart',          healthCheck: '/cart/health',          priority: 50, desiredCount: 1, cpu: 256, memoryMiB: 512 },
  { name: 'order',        repoName: 'ecom/order-service',        containerPort: 8086, pathPrefix: '/orders',        healthCheck: '/orders/health',        priority: 60, desiredCount: 1, cpu: 256, memoryMiB: 512 },
  { name: 'payment',      repoName: 'ecom/payment-service',      containerPort: 8087, pathPrefix: '/payments',      healthCheck: '/payments/health',      priority: 70, desiredCount: 1, cpu: 256, memoryMiB: 512 },
  { name: 'shipping',     repoName: 'ecom/shipping-service',     containerPort: 8088, pathPrefix: '/shipping',      healthCheck: '/shipping/health',      priority: 80, desiredCount: 1, cpu: 256, memoryMiB: 512 },
  { name: 'notification', repoName: 'ecom/notification-service', containerPort: 8089, pathPrefix: '/notifications', healthCheck: '/notifications/health', priority: 90, desiredCount: 1, cpu: 256, memoryMiB: 512 },
  { name: 'review',       repoName: 'ecom/review-service',       containerPort: 8090, pathPrefix: '/reviews',       healthCheck: '/reviews/health',       priority: 100, desiredCount: 1, cpu: 256, memoryMiB: 512 },
];
