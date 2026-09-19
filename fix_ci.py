with open('.github/workflows/dev_build.yml', 'r') as f:
    content = f.read()

# Add permissions block to ensure the release works successfully on this new branch too
permissions_block = '''jobs:
  build:
    runs-on: ubuntu-latest
    permissions:
      contents: write'''

content = content.replace('jobs:\n  build:\n    runs-on: ubuntu-latest', permissions_block)

with open('.github/workflows/dev_build.yml', 'w') as f:
    f.write(content)
