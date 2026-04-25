# ecom-microservices

A 10-service ecommerce reference implementation built with **Spring Boot 4.0 / Java 25**, deployed to **AWS ECS Fargate** via **AWS CDK**, and fronted by **API Gateway (HTTP API) → VPC Link → internal ALB**.

> Status: scaffolded, builds end-to-end, CDK synthesizes 4 stacks, ready to deploy to `ap-northeast-2` (Seoul) with the `aws-4` profile.

---

## Services (10)

| Service               | Port  | Path prefix         | Responsibility                                        |
| --------------------- | ----- | ------------------- | ----------------------------------------------------- |
| auth-service          | 8081  | `/auth`             | register / login / token validation                   |
| user-service          | 8082  | `/users`            | user profile CRUD                                     |
| product-service       | 8083  | `/products`         | product catalog, search                               |
| inventory-service     | 8084  | `/inventory`        | stock check, reserve, release, restock                |
| cart-service          | 8085  | `/cart`             | shopping cart per user, checkout                      |
| order-service         | 8086  | `/orders`           | order placement, status transitions                   |
| payment-service       | 8087  | `/payments`         | charge / refund                                       |
| shipping-service      | 8088  | `/shipping`         | create shipment, tracking, delivery state machine     |
| notification-service  | 8089  | `/notifications`    | send & list notifications                             |
| review-service        | 8090  | `/reviews`          | reviews + per-product rating summary                  |

Every service exposes `GET <prefix>/health` (used by ALB target-group health checks) and Spring Actuator at `/actuator/health`.

---

## Architecture

```
Internet
   │
   ▼
┌──────────────────────┐
│ API Gateway HTTP API │  (public)
└──────────┬───────────┘
           │ VPC Link (private ENIs in our VPC subnets)
           ▼
┌──────────────────────┐
│  Internal ALB :80    │  (private subnets, SG only allows VPC Link SG)
│  path-based routing  │
└──────────┬───────────┘
           │ /auth/*  /users/*  /products/*  /inventory/*  /cart/*
           │ /orders/*  /payments/*  /shipping/*  /notifications/*  /reviews/*
           ▼
┌──────────────────────┐
│ ECS Fargate cluster  │  10 services, 1 task each (default)
│  (private subnets)   │
└──────────────────────┘
```

### Why no NLB in the middle?

You asked whether an NLB is needed between API Gateway and the internal ALB. **No.**

- **API Gateway HTTP API** (apigatewayv2) supports a **VPC Link that targets ALBs directly** — this is what we use here.
- An NLB only becomes mandatory if you use the older **REST API** (apigateway v1) flavor, whose VPC Link only accepts NLBs.

So the chosen path is the simplest production-grade option: HTTP API → VPC Link → internal ALB → Fargate. Lower latency, fewer moving parts, lower bill.

### Network isolation

- The internal ALB's security group only allows **inbound from the API Gateway VPC Link's ENI security group**. Nothing else in the VPC can hit it.
- Fargate task SGs only accept traffic on their container port from the VPC CIDR, and the only thing that talks to them on that port is the ALB.
- All ECS tasks live in `PRIVATE_WITH_EGRESS` subnets and pull images via the NAT gateway (or, optionally, ECR/Logs/STS interface VPC endpoints — see _Cost optimization_ below).

---

## Repo layout

```
ecom-microservices/
├── pom.xml                           # Spring Boot 4.0.6 parent + 10 modules
├── services/
│   ├── auth-service/                 # Spring Boot app + Dockerfile
│   ├── user-service/
│   ├── product-service/
│   ├── inventory-service/
│   ├── cart-service/
│   ├── order-service/
│   ├── payment-service/
│   ├── shipping-service/
│   ├── notification-service/
│   └── review-service/
├── infra/                            # AWS CDK (TypeScript) app
│   ├── bin/ecom-infra.ts             # entry point — instantiates 4 stacks
│   └── lib/
│       ├── services.ts               # routing/port table (single source of truth)
│       ├── ecr-stack.ts              # 10× ECR repos
│       ├── network-stack.ts          # VPC + ECS cluster + internal ALB + listener + SGs
│       ├── ecs-stack.ts              # 10× Fargate services + target groups + listener rules
│       └── api-stack.ts              # HTTP API + VPC Link + ALB integration + routes
├── scripts/
│   ├── build-and-push.sh             # mvn package -> docker build -> ECR push
│   ├── deploy.sh                     # bootstrap -> deploy stacks -> push images -> redeploy
│   └── smoke-test.sh                 # hits every /health through the public API URL
└── scripts/github-actions-ci.yml.example   # CI workflow — copy to .github/workflows/ci.yml after granting the token `workflow` scope
```

---

## Local quickstart

```bash
# 1) Build everything (Java 25 required)
mvn -T 1C package -DskipTests

# 2) Run any service standalone
java -jar services/auth-service/target/auth-service.jar
curl http://localhost:8081/auth/health
```

---

## Deploy to AWS (Seoul, profile `aws-4`)

Prereqs: Docker, Java 25, Maven, Node 20+, the AWS CDK CLI, and the `aws-4` profile configured.

```bash
cd infra
npm ci

export AWS_PROFILE=aws-4
export AWS_REGION=ap-northeast-2

# end-to-end (idempotent)
../scripts/deploy.sh

# afterwards
../scripts/smoke-test.sh
```

`deploy.sh` does the right ordering:

1. `cdk bootstrap`
2. `cdk deploy EcomEcrStack EcomNetworkStack` (creates empty ECR repos + VPC/ALB)
3. `scripts/build-and-push.sh` (mvn → docker → ECR push, both `:latest` and `:<sha>`)
4. `cdk deploy EcomEcsStack EcomApiStack` (Fargate tasks now find their images)
5. `aws ecs update-service --force-new-deployment` for each service so they pick up `:latest`

---

## Stacks

| Stack              | What it owns                                                                                     |
| ------------------ | ------------------------------------------------------------------------------------------------ |
| `EcomEcrStack`     | 10 ECR repos (`ecom/<svc>-service`) with `imageScanOnPush` and a 20-image lifecycle policy.      |
| `EcomNetworkStack` | VPC (2 AZ, 1 NAT), ECS cluster, internal ALB + port-80 listener, ALB SG, VPC Link SG.            |
| `EcomEcsStack`     | 10 Fargate task definitions, services, target groups, and ALB listener rules (path-based).       |
| `EcomApiStack`     | API Gateway HTTP API, VPC Link in private subnets, ALB integration, 20 routes (`/x` + `/x/{proxy+}`). |

---

## CI / CD (next step)

`scripts/github-actions-ci.yml.example` runs `mvn package` and `cdk synth`. To activate it:

```bash
mkdir -p .github/workflows
cp scripts/github-actions-ci.yml.example .github/workflows/ci.yml
git add .github/workflows/ci.yml && git commit -m "ci: enable GitHub Actions" && git push
```

> Note: the agent that scaffolded this repo couldn't push directly under `.github/workflows/` because the personal access token in use lacks the `workflow` scope. Re-running the push from a session with that scope (or from the GitHub UI) is enough.

The natural extension is a `cd.yml` job triggered on `main` that:

1. Configures AWS credentials via OIDC into the same account.
2. Runs `scripts/build-and-push.sh`.
3. Calls `aws ecs update-service --force-new-deployment` per service (or `cdk deploy` for infra changes).

---

## Cost optimization (optional)

- Drop the NAT and add interface VPC endpoints for `ecr.api`, `ecr.dkr`, `s3` (gateway), `logs`, `secretsmanager`, `ssm` — saves the NAT bill once you go past a couple of $/day.
- Use **Fargate Spot** capacity provider for non-critical services.
- Switch `containerInsights` from V2 to disabled if you don't need the metrics.
