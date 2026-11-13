FROM node:22-alpine AS build
WORKDIR /app

ARG VITE_API_URL
ARG VITE_APP_NAME
ARG VITE_DEFAULT_STATION_MODE
ENV VITE_API_URL=$VITE_API_URL
ENV VITE_APP_NAME=$VITE_APP_NAME
ENV VITE_DEFAULT_STATION_MODE=$VITE_DEFAULT_STATION_MODE

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:1.27-alpine
# Substitui a config default do nginx pela nossa, que faz o fallback SPA
# (try_files ... /index.html) e corrige o 404 ao recarregar rotas internas.
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
