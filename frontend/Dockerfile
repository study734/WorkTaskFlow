FROM node:20-alpine AS build
WORKDIR /workspace
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
ARG VITE_PUBLIC_SITE_URL
ARG VITE_API_BASE_URL=/api/v1
ARG VITE_ALLOW_INDEXING=false
ARG VITE_ADMIN_PORT=19091
ENV VITE_PUBLIC_SITE_URL=$VITE_PUBLIC_SITE_URL \
    VITE_API_BASE_URL=$VITE_API_BASE_URL \
    VITE_ALLOW_INDEXING=$VITE_ALLOW_INDEXING \
    VITE_ADMIN_PORT=$VITE_ADMIN_PORT
RUN npm run build

FROM nginx:1.27-alpine
RUN mkdir -p /etc/nginx/snippets
COPY infra/nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /workspace/dist /usr/share/nginx/html
ENV BACKEND_NORMAL=127.0.0.1:8081 \
    BACKEND_ADMIN=127.0.0.1:19092
EXPOSE 80 443
