FROM node:24-bookworm-slim

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates openssl \
    && rm -rf /var/lib/apt/lists/*

ENV NEXT_TELEMETRY_DISABLED=1

COPY package.json package-lock.json ./
COPY apps/control-plane/package.json apps/control-plane/package.json
COPY apps/control-plane/prisma.config.ts apps/control-plane/prisma.config.ts
COPY apps/control-plane/prisma apps/control-plane/prisma
RUN npm ci

COPY . .
RUN npm run db:generate && npm run build

ENV NODE_ENV=production
ENV PORT=3000

EXPOSE 3000

CMD ["npm", "--workspace", "@quickping/control-plane", "run", "start"]
