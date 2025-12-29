# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Ostrm is a modern full-stack application that converts file lists into STRM streaming media files (formerly OpenList to Stream project). The project features containerized architecture with multi-platform deployment, complete user authentication, task scheduling, and media scraping capabilities.

- **Frontend**: Nuxt.js 3.13.0 + Vue 3.4.0 + Tailwind CSS 3.4.15
- **Backend**: Spring Boot 3.3.9 + Gradle 8.12.1 + Java 21
- **Database**: SQLite 3.47.1.0 with Flyway 11.4.0 migrations
- **DevOps**: Docker + Docker Compose + GitHub Actions CI/CD
- **Architecture**: Multi-stage containerized deployment with hot reload support


## Architecture

### High-Level Structure
```
├── frontend/           # Nuxt.js 3.13.0 frontend application
│   ├── pages/         # Auto-routed Vue pages (7 main pages)
│   ├── components/    # Reusable Vue components (AppHeader, etc.)
│   ├── middleware/    # Route middleware (auth.js, guest.js)
│   ├── stores/        # Pinia state management
│   ├── plugins/       # Nuxt plugins and utilities
│   └── assets/        # Tailwind CSS styling and static assets
├── backend/           # Spring Boot 3.3.9 backend application
│   └── src/main/java/com/hienao/openlist2strm/
│       ├── controller/  # REST API controllers (auth, config, tasks)
│       ├── service/     # Business logic layer
│       ├── mapper/      # MyBatis data access layer
│       ├── entity/      # Database entities
│       ├── job/         # Quartz scheduled jobs
│       ├── config/      # Spring configuration classes
│       └── security/    # JWT authentication configuration
├── llmdoc/            # Comprehensive documentation system
│   ├── architecture/   # System architecture documents
│   ├── guides/        # Development and deployment guides
│   ├── sop/           # Standard operating procedures
│   └── agent/         # Analysis reports and technical deep-dives
├── .github/           # GitHub Actions CI/CD workflows
├── docker-compose.yml # Container orchestration
└── dev-docker.sh      # Enhanced development environment script
```

### Key Components

**Frontend (Nuxt.js 3.13.0)**:
- Authentication via JWT tokens stored in cookies with auto-refresh
- Middleware-protected routes (`auth.js`, `guest.js`) with intelligent routing
- Tailwind CSS 3.4.15 with glassmorphism design system
- Composition API with `<script setup>` syntax and TypeScript support
- Pinia state management with localStorage/sessionStorage persistence
- Responsive design with gradient color themes and animations

**Backend (Spring Boot 3.3.9)**:
- RESTful API with JWT authentication and Spring Security integration
- Quartz scheduler for task automation (RAM storage due to SQLite compatibility)
- MyBatis 3.0.4 ORM with SQLite 3.47.1.0 database
- Flyway 11.4.0 for database migrations and version management
- Multi-level caching with Caffeine and async processing
- WebSocket support for real-time updates

**Core Features**:
- OpenList configuration management with CRUD operations
- STRM file generation tasks with batch processing and progress tracking
- Scheduled task execution with Cron expressions and error handling
- AI-powered media scraping (optional) with configurable providers
- URL encoding control and Base URL replacement for network adaptation
- Multi-platform container deployment with health checks

**Documentation System**:
- Comprehensive llmdoc/ documentation with architecture analysis
- Developer guides for environment setup and contribution
- CI/CD automation documentation with GitHub Actions
- Standard operating procedures for releases and maintenance

### Database

- **SQLite 3.47.1.0**: Primary database with file storage and WAL mode
- **Tables**: `openlist_config`, `task_config`, `user_info`, and migration tracking
- **Migrations**: Located in `backend/src/main/resources/db/migration/` with Flyway 11.4.0
- **Path**: `/maindata/db/openlist2strm.db` (standardized container path)
- **Connection Pool**: HikariCP with optimized SQLite configuration
- **Transaction Management**: Spring Boot transaction management with rollback support

### API Structure

Main endpoints:
- `/api/auth/*` - Authentication (login, register, logout, token refresh)
- `/api/openlist-config` - OpenList server configurations (CRUD operations)
- `/api/task-config` - STRM generation task management (scheduling, execution)
- `/api/settings` - Application settings and preferences management
- `/api/scraping` - AI media scraping configuration and execution
- `/ws/*` - WebSocket endpoints for real-time updates

**API Features**:
- OpenAPI 3.0 documentation with Swagger UI
- Global exception handling with structured error responses
- Request validation with Bean Validation annotations
- Rate limiting and CORS configuration for security
- Comprehensive logging with structured JSON output

## Documentation System Navigation

### Main Documentation
- **[CLAUDE.md](CLAUDE.md)** - Project overview and development guidelines (this file)
- **[llmdoc/index.md](llmdoc/index.md)** - Complete documentation system entry point
- **[System Architecture Overview](llmdoc/architecture/system-architecture-overview.md)** - Detailed architecture design
- **[CI/CD Automation Process](llmdoc/guides/ci-cd-automation-process.md)** - Continuous integration and deployment guide
- **[Development Environment Setup](llmdoc/guides/development-environment-setup.md)** - Complete environment configuration

### Analysis Reports
- **[Project Changes Analysis](llmdoc/agent/project-changes-analysis.md)** - Recent architectural and technical changes
- **[Development Experience Optimization](llmdoc/agent/development-experience-optimization.md)** - Tool improvements and best practices
- **[Documentation Integration Analysis](llmdoc/agent/documentation-system-integration.md)** - Documentation strategy and maintenance

### Standard Procedures
- **[Project Release Process](llmdoc/sop/project-release-process.md)** - Version release and update procedures
- **[Database Backup & Recovery](llmdoc/sop/database-backup-recovery.md)** - Data protection and disaster recovery

## Development Environment Setup

### Quick Start
```bash
# Clone the repository
git clone https://github.com/hienao/openlist-strm.git
cd openlist-strm

# Initialize development environment
./dev-docker.sh install

# Start development mode (with hot reload)
./dev-docker.sh start-dev

# Health check
./dev-docker.sh health
```

### Development Modes
```bash
# Production mode (standard build)
./dev-docker.sh start

# Development mode (hot reload enabled)
./dev-docker.sh start-dev

# View real-time logs
./dev-docker.sh logs-f

# Access container shell for debugging
./dev-docker.sh exec
```

### Build and Deployment
```bash
# Build production image
./dev-docker.sh build

# Force rebuild without cache
./dev-docker.sh rebuild --no-cache

# Clean all containers, images, and volumes
./dev-docker.sh clean-all

# Complete rebuild from scratch
./dev-docker-rebuild.sh  # Linux/macOS
dev-docker-rebuild.bat    # Windows
```

### Development URLs
- **Frontend Dev Server**: http://localhost:3000
- **Backend API Server**: http://localhost:8080
- **Main Application**: http://localhost:3111
- **API Documentation**: http://localhost:3111/swagger-ui.html
- **Health Check**: http://localhost:3111/actuator/health

## Development Guidelines

### Frontend Development
- Use Composition API with `<script setup>` syntax and TypeScript
- Apply `auth` middleware to protected pages with intelligent route handling
- Use `$fetch` for API calls with automatic Bearer token injection and error handling
- Follow Tailwind CSS utility-first approach with glassmorphism design system
- Implement responsive design with gradient color themes and smooth animations
- Use Pinia stores for state management with proper persistence strategies

### Backend Development
- Follow Spring Boot 3 conventions with clean layered architecture
- Use `@RestController` with OpenAPI 3.0 annotations for API endpoints
- Implement business logic in `@Service` classes with transaction management
- Create MyBatis mappers with proper SQL mapping and result handling
- Use `@Valid` with Bean Validation for request/response validation
- Implement proper exception handling with `@ControllerAdvice`
- Follow security best practices with Spring Security and JWT

### Database Development
1. Create migration file: `V{version}__{description}.sql` using Flyway conventions
2. Place in `backend/src/main/resources/db/migration/`
3. Test migrations with `./gradlew flywayMigrate` before restart
4. Use proper indexing and foreign key constraints
5. Follow SQLite best practices for performance and concurrency

### Testing Strategy

**Important**: Unless explicitly requested by the user, do not run automated tests after development completion. When testing is required, use only Docker container build scripts for validation.

**When Testing Is Requested**:
```bash
# Use Docker build script for container testing
./dev-docker.sh build

# Verify container starts successfully
./dev-docker.sh start

# Health check to validate deployment
./dev-docker.sh health

# Clean up after testing
./dev-docker.sh clean-all
```

**Available Testing Tools** (use only when explicitly requested):
- **Backend**: JUnit 5 + Spring Boot Test + TestContainers
- **Frontend**: Vitest + Vue Test Utils for component testing
- **Code Quality**: PMD + Spotless (backend), ESLint + Prettier (frontend)
- **Coverage**: `./gradlew jacocoTestReport` (backend), `npm run test:coverage` (frontend)
- **Security**: OWASP dependency check and security scanning
- **Performance**: Load testing and profiling for optimization

**Testing Philosophy**:
- Prioritize container-based integration testing over unit testing
- Use Docker build process as primary validation method
- Focus on functional verification rather than code coverage metrics
- Manual testing through browser interaction when applicable

### Git Workflow
- Follow feature branch workflow with descriptive names
- Use conventional commit messages (`feat:`, `fix:`, `docs:`, etc.)
- Create pull requests for all code changes with proper descriptions
- Ensure all quality checks pass before merging
- Use semantic versioning for releases (`v*.*.*` format)

### Environment Configuration
- Use `.env.docker.example` as template for local development
- Never commit sensitive configuration to repository
- Use different profiles for development (`dev`), testing (`test`), and production (`prod`)
- Configure proper logging levels for each environment
- Use environment variables for all external service connections

## CI/CD and Container Deployment

### Automated Build and Release

**GitHub Actions Workflow**:
- **Triggers**: Tag pushes (`v*.*.*` for releases, `beta-v*.*.*` for beta)
- **Multi-platform**: Supports linux/amd64 and linux/arm64 architectures
- **Security**: Docker provenance and SBOM generation enabled
- **Registry**: Automatic push to Docker Hub registry

**Release Process**:
1. Create version tag: `git tag v1.2.0`
2. Push tag: `git push origin v1.2.0`
3. Automatic GitHub Actions workflow triggers
4. Multi-platform Docker images built and pushed
5. GitHub Release automatically created with assets
6. Documentation updated with latest changes

```bash
# Quick start with latest image
docker-compose up -d

# Development mode with hot reload
./dev-docker.sh start-dev

# Clean rebuild (removes all containers, images, volumes)
./dev-docker-rebuild.sh        # Linux/macOS
dev-docker-rebuild.bat         # Windows

# Manual rebuild commands
docker-compose down --rmi all --volumes
docker-compose build
docker-compose up -d

# Access application
http://localhost:3111
```

### Multi-Platform Architecture Support

**Supported Architectures**:
- **linux/amd64**: Standard x86_64 servers and desktops
- **linux/arm64**: ARM64 servers (AWS Graviton, Raspberry Pi 4+)

**Docker Buildx Integration**:
- Native multi-platform builds using QEMU emulation
- Single command builds all architectures simultaneously
- Optimized layer caching for faster builds
- Automatic manifest creation for multi-arch images

**Pulling Images**:
```bash
# Pull specific architecture image
docker pull --platform linux/amd64 hienao6/ostrm:latest
docker pull --platform linux/arm64 hienao6/ostrm:latest

# Pull multi-arch image (Docker selects appropriate platform)
docker pull hienao6/ostrm:latest
```

For custom path configurations, use environment variables:

1. Copy the environment configuration:
```bash
cp .env.docker.example .env
```

2. Edit `.env` file with your custom paths:
```bash
# Host paths for Docker volumes
LOG_PATH_HOST=./logs           # Log files host path
CONFIG_PATH_HOST=./data/config # Configuration files host path
DB_PATH_HOST=./data/db         # Database files host path
STRM_PATH_HOST=./strm          # STRM files host path
```

**Volume Mappings**:
- `${LOG_PATH_HOST}:/maindata/log` - Application logs
- `${CONFIG_PATH_HOST}:/maindata/config` - Application configuration files
- `${DB_PATH_HOST}:/maindata/db` - Database files
- `${STRM_PATH_HOST}:/app/backend/strm` - Generated STRM files output

**Standardized Path Structure**:
```
Container Internal Path          Host Path (Default)
/maindata/log/                   → ./logs/
/maindata/config/               → ./data/config/
/maindata/db/                   → ./data/db/
/app/backend/strm/              → ./strm/
```

### Docker Rebuild Script (`dev-docker-rebuild.sh`)
- Completely removes existing containers, networks, images, and volumes
- Configures npm registry to Chinese mirror for better connectivity
- Rebuilds all images from scratch with Docker Buildx
- Starts containers in detached mode
- Automatically applies standardized path configuration

### Docker Debug Script
For troubleshooting container issues:
```bash
# Comprehensive container debugging and setup (Linux/macOS/Git Bash)
./docker-debug.sh

# Features:
# - Checks Docker daemon status and Docker Buildx setup
# - Creates/validates .env file from .env.docker.example
# - Creates necessary data directories with standardized structure
# - Validates Flyway migration files and database schema
# - Offers database cleanup and reset options
# - Builds image with --no-cache for clean builds
# - Starts container with proper volume mounts and health checks
# - Applies standardized path configuration automatically
```

**Cross-Platform Docker Scripts**: All Docker scripts have corresponding `.bat` files for Windows.

### Direct Docker Commands
```bash
# Build multi-platform image
docker buildx build -t ostrm:latest --platform linux/amd64,linux/arm64 .

# Run container (single platform)
docker run -d \
  --name ostrm \
  -p 3111:80 \
  -v ./data/config:/maindata/config \
  -v ./data/db:/maindata/db \
  -v ./logs:/maindata/log \
  -v ./strm:/app/backend/strm \
  ostrm:latest
```

### Path Standardization Benefits

1. **Consistency**: All components use standardized internal paths
2. **Backward Compatibility**: Existing deployments continue to work without changes
3. **Flexibility**: Environment variables allow custom host path configurations
4. **Maintainability**: Centralized path management reduces configuration errors
5. **Cross-Platform**: Works consistently across different host operating systems
6. **Container Orchestration**: Optimized for Docker Compose and Kubernetes deployments

## Recent Architecture Updates

### CI/CD Optimization (Latest 5 Commits)
- **Simplified Runner Configuration**: Moved from complex matrix strategy to standard ubuntu-latest runners
- **Native Docker Buildx**: Switched to Docker Buildx native multi-platform builds for better performance
- **Gradle Version Management**: Downgraded to Gradle 8.12.1 for stability and fixed PMD configuration issues
- **Build Performance**: Improved build times with optimized caching strategies
- **Base Image Simplification**: Removed noble variants, using standard Ubuntu 22.04 for consistency

### Development Experience Improvements
- **Enhanced Dev Scripts**: Improved `dev-docker.sh` with better health checks and error handling
- **Hot Reload Stability**: Fixed container file synchronization issues for reliable development
- **Code Quality Tools**: Integrated PMD, Spotless, and comprehensive testing frameworks
- **Documentation System**: Complete llmdoc/ documentation with architecture analysis and guides
- **Multi-Platform Development**: Full support for ARM64 and AMD64 development environments

## Important Notes

- **Testing Strategy**: Prioritize Docker container builds over automated unit tests unless explicitly requested by users
- **Quartz Configuration**: Uses RAM storage (RAMJobStore) instead of database persistence due to SQLite compatibility with job scheduling
- **Authentication**: JWT tokens with auto-refresh mechanism and configurable expiration times
- **CORS**: Configured for development and production environments with proper security headers
- **File Generation**: STRM files are generated in the `/app/backend/strm` directory with batch processing support
- **AI Integration**: Optional AI scraping feature for media metadata with configurable providers
- **Path Management**: Standardized paths ensure consistent behavior across deployment environments with backward compatibility
- **Multi-Platform Support**: Native support for x86_64 and ARM64 architectures with automatic image selection
- **Performance Optimization**: Multi-level caching, async processing, and database connection pooling for optimal performance
- **Security**: Comprehensive security measures including dependency scanning, input validation, and secure configuration management