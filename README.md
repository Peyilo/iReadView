# iRead

iRead is an embeddable Android reading component with a demo host application.

## Project status

This repository is at the project scaffold and product-definition stage. The reader API and reading features have not been implemented yet.

## Modules

- `:app` — demo host application. It should exercise the library only through its public API.
- `:app:libreadview` — Android library containing the reusable reading component.

## Documentation

- [Product requirements](docs/01_PRODUCT_REQUIREMENTS.md)
- [Architecture and project structure](docs/02_ARCHITECTURE_AND_PROJECT_STRUCTURE.md)
- [Implementation handoff](docs/03_IMPLEMENTATION_HANDOFF.md)

## Architecture boundary

The base reader container owns common navigation, input routing, effect lifecycle, and the host area for content presentation. Presentation subclasses decide how content is organized. Paged reading and continuous scrolling are separate presentation implementations; scrolling is not modeled as a page-turn animation.
