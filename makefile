run:
	docker compose --env-file .env up -d

smee:
	node smee.js

down:
	docker compose down

down-v:
	docker compose down -v

monitoring-check:
	@echo App Metrics  : http://localhost:8080/actuator/prometheus
	@echo Prometheus   : http://localhost:9090/targets
	@echo Loki         : http://localhost:3100/ready
	@echo Grafana      : http://localhost:3000