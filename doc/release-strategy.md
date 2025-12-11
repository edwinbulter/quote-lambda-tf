# Release Strategy

This document outlines the process for creating releases in this monorepo using a trunk-based development approach with release branches.

## Overview

- **Trunk Branch**: `main` (always deployable)
- **Release Branches**: `release/x.y.z` (temporary, deleted after release)
- **Tags**: `vx.y.z` (immutable release points)
- **Versioning**: [Semantic Versioning 2.0.0](https://semver.org/)

## Release Process

### 1. Prepare for Release

Ensure all changes are merged to `main` and all tests pass:

```bash
git checkout main
git pull origin main
git fetch --tags --force  # Ensure you have the latest tags from remote
cd quote-lambda-tf-backend && mvn verify && cd ..  # For backend
cd quote-lambda-tf-frontend && npm test && cd ..  # For frontend
```

### 2. Create Release Branch

Create a release branch from `main`:

```bash
git checkout -b release/1.0.0
```

### 3. Update Version Numbers

Update versions in all modified modules:

#### Frontend
```bash
cd quote-lambda-tf-frontend
npm version 1.0.0 --no-git-tag-version
cd ..
```

#### Backend
```bash
cd quote-lambda-tf-backend
mvn versions:set -DnewVersion=1.0.0
mvn versions:commit
cd ..
```

### 4. Commit Version Changes

```bash
git add .
git commit -m "chore: prepare release 1.0.0"
```

### 5. Create Release Tag

```bash
git tag -a v1.0.0 -m "Release 1.0.0"
```

### 6. Push Changes

```bash
git push origin release/1.0.0
git push origin v1.0.0
```

### 7. Create GitHub Release

1. Go to: https://github.com/yourusername/quote-lambda-tf/releases/new
2. Select tag: `v1.0.0`
3. Release title: `Release 1.0.0`
4. Add release notes (changes since last release)
5. Mark as "Latest release"
6. Publish release

### 8. Merge Back to Main

After creating the release, merge the release branch back to `main` to keep it synchronized with production.

```bash
git checkout main
git merge release/1.0.0
git push origin main
git branch -d release/1.0.0           # Delete local branch
git push origin --delete release/1.0.0 # Delete remote branch
```

**Why delete the release branch?**
- The tag `v1.0.0` preserves the exact release state (immutable)
- All changes are now in `main`
- Release branches are temporary by design
- Keeps the branch list clean and prevents confusion
- Use the tag for future reference: `git checkout v1.0.0`

**Why merge back to main?**

This step ensures that `main` reflects the exact state of production:

```
1. Start: main (1.0.0-SNAPSHOT or previous version)
   ↓
2. Create: release/1.0.0 (version changed to 1.0.0, SNAPSHOT removed)
   ↓
3. Tag: v1.0.0 (immutable release point)
   ↓
4. Merge to: main (1.0.0) ← main now matches production
```

**Key benefits:**
- **Version synchronization**: `main` has the release version (`1.0.0`), not `-SNAPSHOT`
- **Production parity**: `main` represents what's actually deployed
- **Hotfix base**: Future hotfixes branch from the correct production version
- **Complete history**: All release changes are preserved in `main`

### 9. Bump Version for Next Development Cycle

Immediately after merging, bump the version to prepare for the next release cycle:

#### Backend
```bash
cd quote-lambda-tf-backend
mvn versions:set -DnewVersion=1.1.0-SNAPSHOT
mvn versions:commit
cd ..
```

#### Frontend
```bash
cd quote-lambda-tf-frontend
npm version 1.1.0-SNAPSHOT --no-git-tag-version
cd ..
```

**Note:** npm accepts any version string, so `1.1.0-SNAPSHOT` works directly. Alternatively, manually edit `package.json` to set `"version": "1.1.0-SNAPSHOT"`.

#### Commit the version bump
```bash
git add .
git commit -m "chore: bump version to 1.1.0-SNAPSHOT for next development cycle"
git push origin main
```

**Why bump to SNAPSHOT?**
- Indicates work-in-progress for the next release
- Prevents confusion between released (`1.0.0`) and development (`1.1.0-SNAPSHOT`) versions
- Standard practice in Maven/Java projects, also useful for npm projects
- Makes it clear that `main` is now working toward version `1.1.0`

## Versioning Rules

1. **MAJOR** version for incompatible API changes
2. **MINOR** version for backward-compatible features
3. **PATCH** version for backward-compatible bug fixes

## Best Practices

1. Always create a release branch for version updates
2. Never push directly to `main` - use PRs
3. Keep release branches short-lived
4. Always test before tagging
5. Write clear release notes

## Example: Patch Release

```bash
# From main
git checkout main
git pull origin main
git fetch --tags --force
git checkout -b release/1.0.1
cd quote-lambda-tf-frontend && npm version patch --no-git-tag-version && cd ..
cd quote-lambda-tf-backend && mvn versions:set -DnewVersion=1.0.1 && mvn versions:commit && cd ..
git add .
git commit -m "chore: prepare release 1.0.1"
git tag -a v1.0.1 -m "Release 1.0.1"
git push origin release/1.0.1
git push origin v1.0.1
# Create GitHub release, then...
git checkout main
git merge release/1.0.1
git push origin main
git branch -d release/1.0.1
git push origin --delete release/1.0.1
# Bump version for next development cycle
cd quote-lambda-tf-backend && mvn versions:set -DnewVersion=1.0.2-SNAPSHOT && mvn versions:commit && cd ..
cd quote-lambda-tf-frontend && npm version 1.0.2-SNAPSHOT --no-git-tag-version && cd ..
git add .
git commit -m "chore: bump version to 1.0.2-SNAPSHOT for next development cycle"
git push origin main
```

## Version Files

### Frontend
- `quote-lambda-tf-frontend/package.json`
- `quote-lambda-tf-frontend/package-lock.json`

### Backend
- `quote-lambda-tf-backend/pom.xml`

## Release Notes

### Location
Create release notes in two places:
1. In the GitHub Release description when creating a release
2. In `CHANGELOG.md` at the root of the repository

### Format
Use the following template for release notes:

```markdown
## [Version] - YYYY-MM-DD

### Added
- New features and additions

### Changed
- Changes in existing functionality

### Deprecated
- Soon-to-be removed features

### Removed
- Removed features

### Fixed
- Bug fixes

### Security
- Security-related changes

### Infrastructure
- Changes to deployment or infrastructure

### Dependencies
- Updated dependencies
```

### Best Practices
1. **Be Specific**: Include issue/PR numbers (e.g., `#123`) and links to them
2. **Group by Type**: Categorize changes (Added, Changed, Fixed, etc.)
3. **Be Consistent**: Use the same format for all releases
4. **Include Impact**: Note if the change affects users, developers, or both
5. **Link to Documentation**: Reference relevant documentation updates

### Example
```markdown
## [1.2.0] - 2025-12-11

### Added
- New quote sharing feature (#123)
- Dark mode support (#124)

### Changed
- Improved quote loading performance (#130)
- Updated UI components to use new design system (#128)

### Fixed
- Fixed issue with quote likes not persisting (#131)
- Resolved mobile layout issues (#135)

### Dependencies
- Updated React to v18.2.0
- Upgraded AWS SDK to latest version
```

### Automating Release Notes
Consider using these tools to generate release notes:
- GitHub's built-in release notes generator
- `standard-version` for automated versioning and changelog generation
- `conventional-changelog` for commit message-based changelogs

To generate a changelog from commit messages:
```bash
# Install conventional-changelog
npm install -g conventional-changelog-cli

# Generate changelog
conventional-changelog -p angular -i CHANGELOG.md -s
```
