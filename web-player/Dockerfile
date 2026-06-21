FROM node:18-alpine AS build

WORKDIR /projects

COPY web-player/package*.json web-player/
RUN cd web-player && npm install

COPY ops ./ops
COPY web-player ./web-player

# New Relic Browser RUM build-time config. Vite reads VITE_* vars and inlines
# them into the bundle at compile time, so they MUST be set here (passing them
# at runtime via `docker run -e ...` does nothing because the bundle is
# already minified by then). All five are optional — if any of the three
# required ones (license/app/account) is blank, the agent stays disabled at
# runtime (see web-player/src/services/newrelic.ts).
ARG VITE_NEWRELIC_LICENSE_KEY=""
ARG VITE_NEWRELIC_APPLICATION_ID=""
ARG VITE_NEWRELIC_ACCOUNT_ID=""
ARG VITE_NEWRELIC_TRUST_KEY=""
ARG VITE_NEWRELIC_AGENT_ID=""
ENV VITE_NEWRELIC_LICENSE_KEY=$VITE_NEWRELIC_LICENSE_KEY \
    VITE_NEWRELIC_APPLICATION_ID=$VITE_NEWRELIC_APPLICATION_ID \
    VITE_NEWRELIC_ACCOUNT_ID=$VITE_NEWRELIC_ACCOUNT_ID \
    VITE_NEWRELIC_TRUST_KEY=$VITE_NEWRELIC_TRUST_KEY \
    VITE_NEWRELIC_AGENT_ID=$VITE_NEWRELIC_AGENT_ID

WORKDIR /projects/web-player
RUN npm run build

FROM nginx:alpine

COPY --from=build /projects/web-player/dist /usr/share/nginx/html
COPY web-player/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 3000

CMD ["nginx", "-g", "daemon off;"]
