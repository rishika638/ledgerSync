# LedgerSync deployment guide

## Status

No AWS or Vercel deployment has been performed. The committed AWS files are not a complete deployable stack:

- `aws/cloudformation.yml` creates a VPC, PostgreSQL RDS instance, a Gemini secret, and an ECS cluster only. It does not create an ECR repository, Redis, ECS task/service, load balancer, TLS listener, task/execution roles, or all secrets referenced by the task definition.
- `aws/ecs-task-definition.json` contains `${...}` placeholders. AWS CLI does not expand these; the referenced cluster/service/roles/secrets must exist, and the task definition must be rendered with actual values before registration.
- `.github/workflows/deploy.yml` is manually dispatched; verification runs by default, while deployment requires explicitly enabling its `deploy` input. It assumes the ECS service, ECR repository, GitHub secrets, and Vercel project are already provisioned. The AWS job rejects unresolved task-definition placeholders and checks that the ECS service exists. The workflow is not evidence that these resources exist or that the deployment is ready.
- `frontend/vercel.json` provides SPA routing only. The API origin is configured at build time with `VITE_API_BASE_URL`; no LedgerSync production URL is supplied.

Do not run the CloudFormation scaffold as a turnkey production deployment. AWS resources can incur charges. The DB password parameter has no default; supply a generated secret rather than a source-controlled credential.

## Local Windows verification

Prerequisites: Docker Desktop, Java 21, Node.js 20+ and npm 10+.

From the repository root, open three PowerShell terminals:

```powershell
# Terminal 1: start PostgreSQL 16 and Redis 7, preserving named volumes
.\scripts\run-local.ps1

# Terminal 2: start Spring Boot on port 8080
.\scripts\start-backend.ps1

# Terminal 3: start Vite on port 5173
.\scripts\start-frontend.ps1
```

The local PostgreSQL host port defaults to 5433 and Redis to 6380 to avoid host-side service collisions. Review `.env` before startup; it is local-only and must not be committed. Check `http://localhost:8080/actuator/health` and `http://localhost:8080/swagger-ui.html`.

## AWS deployment prerequisites and sequence

Complete and validate the infrastructure before using the GitHub workflow:

1. Select the AWS account/region, a verified HTTPS domain and certificate, and a budget. Decide the VPC/subnet topology and whether ECS tasks need NAT or public egress.
2. Provision PostgreSQL 16, Redis 7, ECR, an ECS Fargate cluster and service, task and execution IAM roles, a TLS-terminated load balancer and target group, CloudWatch logs, and security groups allowing only required traffic.
3. Store the database password, browser operator/admin credentials, API keys, Razorpay/Stripe webhook secrets, and optional Gemini key in Secrets Manager. Grant the ECS execution role access only to the exact secret ARNs. Keep API keys and credentials out of task-definition plaintext and frontend build variables.
4. Set the task environment to `SPRING_PROFILES_ACTIVE=prod`, point datasource/Redis variables at the provisioned services, set `LEDGERSYNC_CORS_ALLOWED_ORIGINS` to the exact HTTPS frontend origin, and supply strong distinct browser credentials and API keys.
5. Render every placeholder in `aws/ecs-task-definition.json`; verify that the image, IAM roles, RDS/Redis endpoints, secret ARNs, log group, ECS cluster, service, and health check match the resources you actually created. Run `aws ecs register-task-definition` only after the rendered JSON validates and the referenced resources exist.
6. Build and push the [Dockerfile](../Dockerfile) to the ECR repository, update the ECS service to the registered revision, wait for service stability, and verify `/actuator/health`, Flyway migrations, login, CORS, and a complete finance workflow.
7. Configure the GitHub Actions secrets and names in `.github/workflows/deploy.yml` to match those resources. First run the workflow with deployment disabled; set its `deploy` input to `true` only after the same configuration has been tested from a local AWS CLI session.

The current CloudFormation file does not create all resources above. An operator must extend or replace the scaffold before steps 2–7 can be executed. Do not copy deployment commands from older project documentation that assume an existing `ledgersync-service` or production API URL.

## Vercel deployment

1. Configure a custom HTTPS frontend host and API host under the same registrable domain (for example, `app.example.com` and `api.example.com`). The production session cookie is `Secure` and `SameSite=Lax`; unrelated cross-site hosts are not a supported browser-session configuration.
2. Set `VITE_API_BASE_URL` in the Vercel project's production build environment to `https://YOUR_API_HOST/api/v1`.
3. Set backend `LEDGERSYNC_CORS_ALLOWED_ORIGINS` to the exact frontend origin, such as `https://app.example.com`, and verify credentialed preflight and session requests.
4. From `frontend`, run `npm ci` and `npm run build`, then link/deploy the project using the Vercel account and domain you control:

   ```powershell
   Set-Location frontend
   npm ci
   npm run build
   npx vercel link
   npx vercel --prod
   ```

   Configure project environment variables before deployment. `frontend/vercel.json` only routes SPA paths to `index.html`; it does not proxy API calls.

No account credentials, production URLs, paid resources, or deployment status are included or claimed by this repository.
