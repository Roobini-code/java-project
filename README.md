# Taskboard

A small Java 21 / Spring Boot task tracker with a responsive Thymeleaf frontend and persistent H2 storage. Create tasks, update their status, and remove completed or unwanted tasks.

## Quick start

Run directly with Java 21 and Maven:

```powershell
mvn test
mvn spring-boot:run
```

Then open <http://localhost:8080>.

Or build and run it with Docker Desktop:

```powershell
docker compose up --build --detach
```

Then open <http://localhost:8080>. Compose stores application data in a named volume so it persists when the container stops.

See [LOCAL-AND-DOCKER-GUIDE.md](LOCAL-AND-DOCKER-GUIDE.md) for Windows installation steps, complete local commands, deployment commands, and data persistence notes. For AWS provisioning, SSH access, and EC2 deployment, see [EC2-DEPLOYMENT-GUIDE.md](EC2-DEPLOYMENT-GUIDE.md).

For GitHub Actions setup, required repository secrets, and CI/CD deployment steps, see [GITHUB-ACTIONS-SETUP.md](GITHUB-ACTIONS-SETUP.md).
