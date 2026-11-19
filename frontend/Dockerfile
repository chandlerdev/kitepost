FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:22-alpine AS build
WORKDIR /app
# The /api proxy target is fixed at build time (Next.js writes rewrites into the build).
ARG BACKEND_URL=http://backend:8080
ENV BACKEND_URL=$BACKEND_URL NEXT_TELEMETRY_DISABLED=1
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 PORT=3000 HOSTNAME=0.0.0.0
RUN addgroup -S kitepost && adduser -S kitepost -G kitepost
COPY --from=build --chown=kitepost:kitepost /app/.next/standalone ./
COPY --from=build --chown=kitepost:kitepost /app/.next/static ./.next/static
COPY --from=build --chown=kitepost:kitepost /app/public ./public
USER kitepost
EXPOSE 3000
CMD ["node", "server.js"]
