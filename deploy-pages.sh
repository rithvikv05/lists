#!/bin/bash
set -e

echo "🚀 Building WebAssembly web distribution..."
./gradlew :wasmApp:wasmJsBrowserDistribution --no-daemon

echo "📦 Preparing deployment artifacts..."
rm -rf /tmp/gh-pages-deploy
mkdir -p /tmp/gh-pages-deploy
cp -r wasmApp/build/dist/wasmJs/productionExecutable/* wasmApp/build/dist/wasmJs/productionExecutable/.nojekyll /tmp/gh-pages-deploy/

cd /tmp/gh-pages-deploy
git init
git checkout -b gh-pages
git add .
git commit -m "deploy: update WebAssembly web app on GitHub Pages"
git remote add origin https://github.com/rithvikv05/lists.git
git push -f origin gh-pages

echo "✅ Successfully deployed to gh-pages branch!"
echo "🌐 Your site is live at: https://rithvikv05.github.io/lists/"
