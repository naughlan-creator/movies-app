# syntax=docker/dockerfile:1

# ---- Stage 1: build the static site ----
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY public/ public/
COPY src/ src/

# REACT_APP_* values are compiled into the JavaScript the browser downloads, so they're public:
# URLs and client ids only, never secrets. They're fixed per image, so build one image per environment.
ARG REACT_APP_API_BASE_URL=http://localhost:18085
ARG REACT_APP_OIDC_AUTHORITY=http://localhost:18180/realms/movie-gold
ARG REACT_APP_OIDC_CLIENT_ID=movies-frontend
ENV REACT_APP_API_BASE_URL=$REACT_APP_API_BASE_URL \
    REACT_APP_OIDC_AUTHORITY=$REACT_APP_OIDC_AUTHORITY \
    REACT_APP_OIDC_CLIENT_ID=$REACT_APP_OIDC_CLIENT_ID
RUN npm run build

# ---- Stage 2: serve the files (no Node.js in the final image) ----
FROM caddy:2.11.4-alpine
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/build /srv
