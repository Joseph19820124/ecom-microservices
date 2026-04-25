# Ecom Microservices on AWS Fargate

A reference e-commerce platform composed of **10 Spring Boot 4.0 / Java 25** microservices, deployed to **AWS ECS Fargate** with **AWS CDK** (TypeScript). Public ingress is **API Gateway (HTTP API) → VPC Link → internal ALB → Fargate tasks**.

## Services (10)

| # | Service | Port | Path prefix | Responsibility |
|---|---|---|---|---|
| 1 | `auth-service`         | 8081 | `/auth`          | Login / register / token validation |
| 2 | `user-service`         | 8082 | `/users`         | User profiles |
| 3 | `product-service`      | 8083 | `/products`      | Product catalog & search |
| 4 | `inventory-service`    | 8084 | `/inventory`     | Stock check / reserve / release |
| 5 | `cart-service`         | 8085 | `/cart`          | Shopping cart (买) |
| 6 | `order-service`        | 8086 | `/orders`        | Order management |
| 7 | `payment-service`      | 8087 | `/payments`      | Charge / refund / status |
| 8 | `shipping-service`     | 8088 | `/shipping`      | Shipment creation & tracking |
| 9 | `notification-service` | 8089 | `/notifications` | Email/SMS notifications (stub) |
| 10 | `review-service`      | 8090 | `/reviews`       | Product reviews & ratings |

All services expose `/actuator/health` for ALB target-group health checks.

## Architecture

```
                        ┌──────────────────────────┐
                        │  API Gateway (HTTP API)  │   public ingress
                        └─────────────┬────────────┘
                                      │  VPC Link (private subnets)
                                      ▼
                        ┌──────────────────────────┐
                        │   Internal ALB (HTTP)    │   path-based routing
                        └─────┬──────┬──────┬──────┘
              /auth/*   /users/*   ...    /reviews/*
                  │         │              │
                  ▼         ▼              ▼
              auth-svc   user-svc  ...  review-svc      (10 ECS Fargate services)
              (TG-A)     (TG-U)         (TG-R)          one target group each
```

### Why this shape?

- **HTTP API VPC Link supports ALB directly** — no NLB hop needed (REST API VPC Link still requires an NLB; HTTP API removed that limitation). Cheaper, fewer moving parts, lower latency.
- **Internal ALB** is the right L7 ingress for path-based routing across many services and gives us native health checks, sticky sessions if needed, WebSocket support, etc.
- **Single ALB + listener rules** rather than one ALB per service to keep cost low; each service gets its own target group and listener rule.
- The **VPC Link security group** is the only ingress allowed on the ALB, so the ALB is genuinely private.

If you ever switch back to **API Gateway REST API** (e.g. for resource policies, request validation, usage plans), the topology becomes `REST API → VPC Link → NLB → internal ALB → Fargate`. The CDK code can be extended to add the NLB in front; the rest is unchanged.

## Repo layout

```
.
├── pom.xml                      # parent Spring Boot 4.0 / Java 25 POM
├── services/                    # 10 Maven modules, one per microservice
│   ├── auth-service/
│   ├── user-service/
│   └── ...
├── infra/                       # AWS CDK app (TypeScript)
│   ├── bin/ecom-infra.ts
│   ├── lib/
│   │   ├── ecr-stack.ts
│   │   ├── network-stack.ts     # VPC, internal ALB, SGs, ECS cluster
│   │   ├── ecs-stack.ts         # 10 Fargate services + listener rules
│   │   ├── api-stack.ts         # HTTP API + VPC Link + ALB integration
│   │   └── services.ts          # service catalog driving the loops
├── scripts/
│   ├── build-and-push.sh        # mvn package + docker build + ecr push
│   └── deploy.sh                # bootstrap → deploy → push → deploy
└── .github/workflows/ci.yml     # build + cdk synth on PRs
```

## Local build

```bash
# JDK 25 + Maven 3.9+ required
mvn -q package -DskipTests

# Run a single service locally
java -jar services/auth-service/target/auth-service.jar
curl localhost:8081/auth/health
```

## Deploy to AWS (CLI mode)

This is the "use the AWS CLI/SDK directly" path the team wants before CI/CD lands.

```bash
export AWS_PROFILE=aws-4
export AWS_REGION=ap-northeast-2          # Seoul
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

# One-shot: bootstrap, deploy ECR+VPC, push images, deploy ECS+API
./scripts/deploy.sh
```

Outputs printed at the end:

- `EcomNetworkStack.AlbDnsName` – internal ALB DNS (private only)
- `EcomApiStack.ApiUrl`          – public API Gateway endpoint

### Smoke test

```bash
API=$(aws cloudformation describe-stacks --stack-name EcomApiStack \
        --query "Stacks[0].Outputs[?OutputKey=='ApiUrl'].OutputValue" --output text)

curl  "$API/auth/health"
curl  "$API/products"
curl -X POST "$API/auth/login" -H 'content-type: application/json' \
     -d '{"username":"alice","password":"pw"}'
curl -X POST "$API/cart/alice/items" -H 'content-type: application/json' \
     -d '{"productId":"p-1","qty":2}'
curl -X POST "$API/payments/charge" -H 'content-type: application/json' \
     -d '{"orderId":"o-1","amountCents":1999}'
```

## Roadmap

- Replace in-memory state with DynamoDB / Aurora Serverless v2 per service.
- Add service-to-service auth via IAM SigV4 or short-lived JWT.
- Add request validation & WAF in front of API Gateway.
- Add CodePipeline / GitHub Actions OIDC role for full CI/CD (placeholder workflow already in `.github/workflows/ci.yml`).
- X-Ray + ADOT collector sidecar for distributed tracing.
