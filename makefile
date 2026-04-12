run:
	docker compose --env-file .env up -d

down:
	docker compose down

down-v:
	docker compose down -v