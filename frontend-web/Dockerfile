# F!NT 프론트엔드 (Next.js 14, standalone) 멀티스테이지 빌드

# ========== Deps Stage ==========
FROM node:20-alpine AS deps
WORKDIR /app

# pnpm 활성화 (lockfile v9.0 = pnpm 9)
RUN corepack enable && corepack prepare pnpm@9 --activate

COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

# ========== Build Stage ==========
FROM node:20-alpine AS builder
WORKDIR /app
RUN corepack enable && corepack prepare pnpm@9 --activate

# NEXT_PUBLIC_* 는 빌드 시점에 번들에 박힘 → ARG 로 주입
ARG NEXT_PUBLIC_API_URL
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL
ENV NEXT_TELEMETRY_DISABLED=1

COPY --from=deps /app/node_modules ./node_modules
COPY . .

RUN pnpm build

# ========== Runtime Stage ==========
FROM node:20-alpine AS runner
WORKDIR /app

ENV TZ=Asia/Seoul
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

# 비루트 유저 (Next.js 공식 권장)
RUN addgroup --system --gid 1001 nodejs \
 && adduser --system --uid 1001 nextjs

# standalone 산출물만 복사 (이미지 크기 최소화)
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static
COPY --from=builder --chown=nextjs:nodejs /app/public ./public

USER nextjs
EXPOSE 3000

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
  CMD wget -q --spider http://127.0.0.1:3000/ || exit 1

CMD ["node", "server.js"]
