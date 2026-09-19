with open('.github/workflows/dev_build.yml', 'r') as f:
    content = f.read()

# Need to add permissions for the release step because "Resource not accessible by integration - https://docs.github.com/rest/releases/releases#update-a-release" means GITHUB_TOKEN doesn't have write permissions.

permissions_block = '''jobs:
  build:
    runs-on: ubuntu-latest
    permissions:
      contents: write'''

content = content.replace('jobs:\n  build:\n    runs-on: ubuntu-latest', permissions_block)

with open('.github/workflows/dev_build.yml', 'w') as f:
    f.write(content)
