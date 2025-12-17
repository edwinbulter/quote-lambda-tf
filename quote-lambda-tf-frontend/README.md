# Quote Frontend

This web app uses the [quote-lambda-tf-backend](../quote-lambda-tf-backend/README.md) API for showing and liking quotes from [ZenQuotes.io](https://zenquotes.io/)


## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Live Demo](#live-demo)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Development](#development)
  - [Available Scripts](#available-scripts)
- [Testing](#testing)
  - [Playwright End-to-End Tests](#playwright-end-to-end-tests)
    - [Available Test Suites](#available-test-suites)
    - [Run all tests](#run-all-tests)
    - [Run tests in UI mode](#run-tests-in-ui-mode)
    - [Run a specific test file](#run-a-specific-test-file)
    - [Run tests in headed mode](#run-tests-in-headed-mode)
    - [View test report](#view-test-report)
    - [Debug tests](#debug-tests)
    - [Run tests on specific browsers](#run-tests-on-specific-browsers)
    - [CI/CD Integration](#cicd-integration)
- [Deployment](#deployment)
  - [GitHub Actions Workflow](#github-actions-workflow)
    - [Prerequisites](#prerequisites)
    - [Manual Trigger](#manual-trigger)
  - [Infrastructure Deployment](#infrastructure-deployment)
    - [Key Infrastructure Components](#key-infrastructure-components)
    - [First-Time Setup](#first-time-setup)

## Features

![quote-web-screenshot](public/quote-web-screenshot.png)

### Core Features

- **Random Quote Retrieval**
    - Requests a new random quote and sends the IDs of all previously received quotes to avoid duplicates
    - New Quote button is disabled while loading (displays "Loading...")
    - Unauthenticated users get random quotes without view tracking

- **Quote Navigation**
    - Walk through all received quotes with navigation buttons:
        - **Previous Button**: Shows previous quote (disabled if at start)
        - **Next Button**: Shows next quote (disabled if at end)
        - **First Button**: Jump to first received quote
        - **Last Button**: Jump to last received quote

### Authentication & Authorization

- **Sign In / Sign Out**
    - AWS Cognito integration with email/password authentication
    - Google OAuth sign-in support
    - Automatic role assignment (USER role for regular users)
    - Sign In button shows "Sign Out" when authenticated

- **User Profile**
    - View authenticated user's email and username
    - Profile accessible via user avatar button
    - Secure sign-out functionality

### Favourite Quotes Management

- **Like Button**
    - Like current quote (only enabled for authenticated users with USER role)
    - Disabled while liking is in progress (displays "Liking...")
    - Disabled when quote is already liked
    - Liked quotes automatically added to end of favourites list

- **Favourites Component**
    - Displays all liked quotes in custom order
    - Shows count of liked quotes
    - Refresh button to reload favourites
    - Visible in sidebar when not in management mode

- **Manage Favourites Screen** (NEW)
    - Access via "Manage" button (only visible when authenticated)
    - **Main Menu**: Choose between managing favourites or viewing history
    - **Manage Favourites View**:
        - Drag-and-drop reordering of liked quotes
        - Delete favourites with optimistic UI updates
        - Automatic order synchronization with backend
        - Real-time error handling with rollback
    - **View History View**:
        - Browse all viewed quotes in chronological order
        - See which quotes you've already viewed
        - Helps avoid duplicate recommendations

### View History Tracking

- **Automatic View Recording** (Authenticated users only)
    - Every quote fetched by authenticated users is recorded as viewed
    - Viewed quotes are automatically excluded from future recommendations
    - Prevents seeing the same quote twice in a session

- **View History Access**
    - Access via Management Screen
    - Shows all previously viewed quotes in order
    - Helps track your browsing history

## Tech Stack

### Frontend
- **Framework**: React 18 with TypeScript
- **Build Tool**: Vite
- **Styling**: CSS Modules & SCSS
- **Authentication**: AWS Amplify Auth
- **HTTP Client**: Fetch API
- **State Management**: React Hooks & Context API

### Testing & DevOps
- **E2E Testing**: Playwright
- **Infrastructure as Code**: Terraform
- **Hosting**: AWS (S3 + CloudFront CDN)
- **CI/CD**: GitHub Actions

### Key Dependencies
- **aws-amplify** - AWS authentication and API integration
- **react** - UI framework
- **typescript** - Type safety
- **vite** - Build tool and dev server

## Live Demo

Access the live application at:

**Production Environment:**
> https://d5ly3miadik75.cloudfront.net/

**Development Environment:**
> https://d1fzgis91zws1k.cloudfront.net/

## Project Structure

```
quote-lambda-tf-frontend/
├── src/
│   ├── components/
│   │   ├── Login.tsx                    # Authentication component
│   │   ├── FavouritesComponent.tsx      # Favourites sidebar
│   │   ├── ManagementScreen.tsx         # Management main menu
│   │   ├── ManageFavouritesScreen.tsx   # Reorder/delete favourites
│   │   └── ViewedQuotesScreen.tsx       # View history
│   ├── contexts/
│   │   └── AuthContext.tsx              # Authentication state management
│   ├── api/
│   │   └── quoteApi.ts                  # API client functions
│   ├── types/
│   │   └── Quote.ts                     # TypeScript interfaces
│   ├── App.tsx                          # Main application component
│   ├── App.scss                         # Application styles
│   └── main.tsx                         # Entry point
├── playwright-tests/
│   ├── open-screen.spec.ts              # Initial load tests
│   ├── click-new-quote.spec.ts          # Quote fetching tests
│   └── click-like.spec.ts               # Like functionality tests
├── infrastructure/                      # Terraform configuration
│   ├── bootstrap/                       # Initial setup
│   └── *.tf                             # Infrastructure files
├── public/
│   └── quote-web-screenshot.png         # Screenshot
├── doc/
│   ├── infrastructure.md
│   └── github-workflows.md
├── package.json                         # Dependencies
├── vite.config.ts                       # Vite configuration
├── playwright.config.ts                 # Playwright configuration
└── tsconfig.json                        # TypeScript configuration
```

## Getting Started

1. Clone the repository:
   ```bash
   git clone https://github.com/edwinbulter/quote-lambda-tf.git
   cd quote-lambda-tf
   cd quote-lambda-tf-frontend
   ```

2. Install dependencies:
   ```bash
   npm install
   ```

3. Start the development server:
   ```bash
   npm run dev
   ```

4. Open [http://localhost:5173](http://localhost:5173) in your browser.

## Development

### Available Scripts

- `npm run dev` - Start development server
- `npm run build` - Build for production
- `npm run preview` - Preview production build locally
- `npm run test` - Run tests
- `npm run test:watch` - Run tests in watch mode
- `npm run lint` - Run ESLint
- `npm run format` - Format code with Prettier

## Testing

### Playwright End-to-End Tests

The project uses Playwright for end-to-end testing. Tests are located in the `playwright-tests/` directory and automatically start the development server before running.

#### Available Test Suites

- **`open-screen.spec.ts`** - Tests initial screen load and quote display
- **`click-new-quote.spec.ts`** - Tests New Quote button functionality and duplicate request prevention
- **`click-like.spec.ts`** - Tests Like button and Favourite Quotes component integration

#### Run all tests

```bash
npx playwright test
```

#### Run tests in UI mode

UI mode provides an interactive interface to run and debug tests:

```bash
npx playwright test --ui
```

#### Run a specific test file

```bash
npx playwright test open-screen.spec.ts
npx playwright test click-new-quote.spec.ts
npx playwright test click-like.spec.ts
```

#### Run tests in headed mode

To see the browser while tests are running:

```bash
npx playwright test --headed
```

#### View test report

After running tests, view the HTML report:

```bash
npx playwright show-report
```

#### Debug tests

To debug a specific test:

```bash
npx playwright test --debug
```

#### Run tests on specific browsers

```bash
# Run on Chromium only
npx playwright test --project=chromium

# Run on Firefox only
npx playwright test --project=firefox

# Run on WebKit only
npx playwright test --project=webkit
```

#### CI/CD Integration

Playwright tests can be run in GitHub Actions using the workflow file `.github/workflows/playwright.yml`. This workflow can be triggered manually from the GitHub Actions UI.

## Deployment

### GitHub Actions Workflow

The project includes a GitHub Actions workflow [deploy-frontend.yml](../.github/workflows/deploy-frontend.yml) for building and deploying the frontend and a workflow [playwright.yml](../.github/workflows/playwright.yml) for running the Playwright tests.

Look in [doc/github-workflows.md](doc/github-workflows.md) for more information.


### Infrastructure Deployment

The infrastructure is managed using Terraform. Refer to [doc/infrastructure.md](doc/infrastructure.md) for detailed instructions on setting up and managing the AWS resources.

