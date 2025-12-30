#!/bin/bash
#
# Release script for SupplyLines
#
# Usage:
#   ./release.sh <command>
#
# Commands:
#   major           Bump major version (1.0.0 -> 2.0.0)
#   minor           Bump minor version (1.0.0 -> 1.1.0)
#   patch           Bump patch version (1.0.0 -> 1.0.1)
#   alpha           Start or bump alpha (1.0.0 -> 1.0.1-alpha.1, or 1.0.1-alpha.1 -> 1.0.1-alpha.2)
#   beta            Start or bump beta (1.0.0-alpha.2 -> 1.0.0-beta.1, or 1.0.0-beta.1 -> 1.0.0-beta.2)
#   rc              Start or bump RC (1.0.0-beta.2 -> 1.0.0-rc.1, or 1.0.0-rc.1 -> 1.0.0-rc.2)
#   stable          Promote to stable (1.0.0-rc.1 -> 1.0.0)
#   <version>       Set explicit version (e.g., 1.2.3-alpha.1)
#
# Examples:
#   ./release.sh alpha         # 1.0.0-alpha.1 -> 1.0.0-alpha.2
#   ./release.sh beta          # 1.0.0-alpha.2 -> 1.0.0-beta.1
#   ./release.sh stable        # 1.0.0-rc.1 -> 1.0.0
#   ./release.sh minor         # 1.0.0 -> 1.1.0
#   ./release.sh 2.0.0-alpha.1 # Explicit version
#

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

CURRENT=$(grep 'supplylines_version=' gradle.properties | cut -d'=' -f2)

parse_version() {
    local ver="$1"
    if [[ "$ver" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)(-([a-zA-Z]+)\.([0-9]+))?$ ]]; then
        MAJOR="${BASH_REMATCH[1]}"
        MINOR="${BASH_REMATCH[2]}"
        PATCH="${BASH_REMATCH[3]}"
        PRERELEASE_TYPE="${BASH_REMATCH[5]}"
        PRERELEASE_NUM="${BASH_REMATCH[6]}"
        return 0
    fi
    return 1
}

build_version() {
    if [ -n "$PRERELEASE_TYPE" ]; then
        echo "${MAJOR}.${MINOR}.${PATCH}-${PRERELEASE_TYPE}.${PRERELEASE_NUM}"
    else
        echo "${MAJOR}.${MINOR}.${PATCH}"
    fi
}

if ! parse_version "$CURRENT"; then
    echo -e "${RED}Error: Could not parse current version '${CURRENT}'${NC}"
    exit 1
fi

echo -e "${BLUE}Current version: ${CURRENT}${NC}"

CMD="${1:-}"
if [ -z "$CMD" ]; then
    echo ""
    echo "Usage: ./release.sh <command>"
    echo ""
    echo "Commands:"
    echo "  major    - Bump major (${MAJOR}.${MINOR}.${PATCH} -> $((MAJOR+1)).0.0)"
    echo "  minor    - Bump minor (${MAJOR}.${MINOR}.${PATCH} -> ${MAJOR}.$((MINOR+1)).0)"
    echo "  patch    - Bump patch (${MAJOR}.${MINOR}.${PATCH} -> ${MAJOR}.${MINOR}.$((PATCH+1)))"
    echo "  alpha    - Start/bump alpha pre-release"
    echo "  beta     - Start/bump beta pre-release"
    echo "  rc       - Start/bump release candidate"
    echo "  stable   - Promote to stable release"
    echo "  <ver>    - Set explicit version"
    exit 0
fi

case "$CMD" in
    major)
        MAJOR=$((MAJOR + 1))
        MINOR=0
        PATCH=0
        PRERELEASE_TYPE=""
        PRERELEASE_NUM=""
        ;;
    minor)
        MINOR=$((MINOR + 1))
        PATCH=0
        PRERELEASE_TYPE=""
        PRERELEASE_NUM=""
        ;;
    patch)
        PATCH=$((PATCH + 1))
        PRERELEASE_TYPE=""
        PRERELEASE_NUM=""
        ;;
    alpha)
        if [ "$PRERELEASE_TYPE" = "alpha" ]; then
            PRERELEASE_NUM=$((PRERELEASE_NUM + 1))
        else
            [ -z "$PRERELEASE_TYPE" ] && PATCH=$((PATCH + 1))
            PRERELEASE_TYPE="alpha"
            PRERELEASE_NUM=1
        fi
        ;;
    beta)
        if [ "$PRERELEASE_TYPE" = "beta" ]; then
            PRERELEASE_NUM=$((PRERELEASE_NUM + 1))
        else
            PRERELEASE_TYPE="beta"
            PRERELEASE_NUM=1
        fi
        ;;
    rc)
        if [ "$PRERELEASE_TYPE" = "rc" ]; then
            PRERELEASE_NUM=$((PRERELEASE_NUM + 1))
        else
            PRERELEASE_TYPE="rc"
            PRERELEASE_NUM=1
        fi
        ;;
    stable)
        if [ -z "$PRERELEASE_TYPE" ]; then
            echo -e "${RED}Error: Already a stable version${NC}"
            exit 1
        fi
        PRERELEASE_TYPE=""
        PRERELEASE_NUM=""
        ;;
    *)
        if ! parse_version "$CMD"; then
            echo -e "${RED}Error: Invalid version format '${CMD}'${NC}"
            echo "Expected: MAJOR.MINOR.PATCH or MAJOR.MINOR.PATCH-PRERELEASE.N"
            exit 1
        fi
        ;;
esac

NEW_VERSION=$(build_version)
TAG="v${NEW_VERSION}"

echo -e "${GREEN}New version: ${NEW_VERSION}${NC}"

CHANGES=$(git status --porcelain | grep -v gradle.properties || true)
if [ -n "$CHANGES" ]; then
    echo -e "${RED}Error: Uncommitted changes detected${NC}"
    git status --short
    exit 1
fi

if git rev-parse "$TAG" >/dev/null 2>&1; then
    echo -e "${RED}Error: Tag ${TAG} already exists${NC}"
    exit 1
fi

read -p "Proceed with release ${NEW_VERSION}? [y/N] " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Aborted."
    exit 0
fi

sed -i "s/supplylines_version=.*/supplylines_version=${NEW_VERSION}/" gradle.properties

echo -e "${YELLOW}Building...${NC}"
./gradlew clean build --no-daemon -q

echo -e "${GREEN}Build successful!${NC}"
./gradlew versionInfo --no-daemon -q

git add gradle.properties
git commit -m "Release ${NEW_VERSION}"
git tag -a "$TAG" -m "SupplyLines ${TAG}"

echo -e "${YELLOW}Pushing to origin...${NC}"
BRANCH=$(git rev-parse --abbrev-ref HEAD)
GIT_SSH_COMMAND="ssh" git push origin "$BRANCH"
GIT_SSH_COMMAND="ssh" git push origin "$TAG"

echo ""
echo -e "${GREEN}Release ${NEW_VERSION} pushed successfully!${NC}"
echo ""
echo "Undo locally:  git reset --hard HEAD~1 && git tag -d ${TAG}"
echo "Undo remote:   git push origin :${TAG} && git push origin ${BRANCH} --force"
