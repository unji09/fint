# Alertmanager 기본 이미지(prom/alertmanager)는 busybox 기반이라 tzdata 미포함.
# 한국 시간(KST) 표기를 위해 alpine에서 timezone 데이터만 복사.
FROM alpine:3.19 AS tz
RUN apk add --no-cache tzdata

FROM prom/alertmanager:v0.27.0
COPY --from=tz /usr/share/zoneinfo/Asia/Seoul /etc/localtime
ENV TZ=Asia/Seoul
