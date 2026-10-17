# cronflower-docker

Docker packaging for cronflower: the three image definitions, and a ready to run
[`docker-compose.yaml`](docker-compose.yaml) best-practice cluster template.

## Images

| Image | Dockerfile | Contents |
|-------|------------|----------|
| `fredfeng033/cronflower-server-api` | `Dockerfile.server` | the scheduler (cronsmith + cronflow server) |
| `fredfeng033/cronflower-ui` | `Dockerfile.web` | the Angular console on a zero-dependency Node server |
| `fredfeng033/cronflower-executor-example` | `Dockerfile.executor` | the demo executor (optional) |

The images are built and published by [`.github/workflows/docker-publish.yml`](../../.github/workflows/docker-publish.yml)
on every `v*` tag. Each run overwrites `:latest` and also pushes `:x.y.z` and `:x.y`, so users always
pull `:latest`.

## Run the cluster

No build needed, the compose template pulls the published images.

```bash
docker compose up -d                       # 3 schedulers (each on its own H2) + console
# open http://localhost:7200  (sign in admin / admin123)

docker compose --profile example up -d     # also start the demo executor
docker compose pull && docker compose up -d # upgrade to the latest images
docker compose down                        # stop (add -v to drop the H2 volumes)
```

Each node runs on its own embedded H2 by default, replicated by the leader. For a shared PostgreSQL
(which also unlocks group sharding), follow "SHARED POSTGRESQL (optional)" at the bottom of
[`docker-compose.yaml`](docker-compose.yaml). That header also lists the values to change before a
real deployment (JWT secret, demo accounts).

## Build the images by hand

CI stages the prebuilt artifacts into this directory before building. To reproduce locally, from the
repo root:

```bash
# 1. backend jars
(cd backend && ./mvnw -q -s ../.github/maven-settings.xml -DskipTests package)
mkdir -p backend/cronflower-docker/bin
cp backend/cronflow-server-api/target/cronflow-server-api-*.jar          backend/cronflower-docker/bin/
cp backend/cronflow-executor-example/target/cronflow-executor-example-*.jar backend/cronflower-docker/bin/

# 2. frontend SPA + its server
(cd frontend && cp -n .env.example .env && npm ci && npm run build -- --configuration production)
mkdir -p backend/cronflower-docker/web-dist
cp -r frontend/dist/cronflower/browser/* backend/cronflower-docker/web-dist/
cp deploy/web-server.mjs                   backend/cronflower-docker/web-server.mjs

# 3. build
cd backend/cronflower-docker
docker build -f Dockerfile.server   -t fredfeng033/cronflower-server-api:latest .
docker build -f Dockerfile.web      -t fredfeng033/cronflower-ui:latest .
docker build -f Dockerfile.executor -t fredfeng033/cronflower-executor-example:latest .
```

`bin/`, `web-dist/`, and `web-server.mjs` are build outputs and are git-ignored.
