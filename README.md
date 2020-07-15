# Jsql

Execute SQL on any json data.

```bash
echo '[{"id": "1", "content": {"price": 2.3}}, {"id": "2", "content": {"price": 2.3}}]' | \
    jsql query 'SELECT id, data.content.price FROM data' 

┌────┬───────┐
│ id │ price │
├────┼───────┤
│ 1  │ 2.3   │
├────┼───────┤
│ 2  │ 2.3   │
└────┴───────┘
```

JSql is powered by [Apache Calcite](https://calcite.apache.org) and enables a lot of SQL functionalities including window functions. To know about the permitted SQL syntax and the available functions, visit the [Apache Calcite SQL Reference page](https://calcite.apache.org/docs/reference.html). 

## Install

### Using brew (MacOs & Linux)

Requires a JDK 11 or higher.

```bash
brew tap wlezzar/jsql https://github.com/wlezzar/jsql
brew install wlezzar/jsql/jsql

# use jsql
jsql --help
```

### Using docker (All platforms)

```bash
docker run -i --rm wlezzar/jsql:latest --help
```

To have a native like CLI experience, you can create an alias in your `~/.bashrc` or `~/.zshrc`:

```bash
alias jsql='docker run -i --rm wlezzar/jsql:latest'

jsql --help
```

### Manual Tarball install (All platforms)

Requires a JDK 11 or higher.

```bash
# Get the latest release version
JSQL_VERSION=$(curl -s https://api.github.com/repos/wlezzar/jsql/releases/latest | jq -r .tag_name)

# Download the tar (or zip) package and unarchive it somewhere in your host (ex. /opt)
wget -O /tmp/jsql.zip https://github.com/wlezzar/jsql/releases/download/0.2.0/jsql.zip
unzip /tmp/jsql.zip -d /opt

# Add jsql in your path (add the following line to your ~/.bashrc or ~/.zshrc to make it permanent)
export PATH="${PATH}:/opt/jsql/bin"

# Check that this is working
jsql --help
```

## Getting started

`jsql` can SQL query any json data piped into it. It handles nested data and both streaming and non streaming input.

Input data is available in `jsql` under the `data` table name. Thus, you can use: `SELECT * FROM data` to access it.

To inspect how jsql is parsing the input data, use the `describe` command:

```bash
echo '[{"id": 1}, {"id": 2}]' | jsql describe
```

Then, to query it:

```bash
echo '[{"id": 1}, {"id": 2}]' | jsql query 'SELECT id FROM data'
```

If you expect streaming data (one json per line), use the `--streaming` option:

```bash
curl -s  https://stream.wikimedia.org/v2/stream/recentchange \
    | grep data \
    | grep '/mediawiki/recentchange/1.0.0' \
    | sed 's/^data: //g' \
    | jsql --streaming --take 10 query "SELECT id, title, server_name FROM data WHERE server_name = 'www.wikidata.org'"
```

## Getting help

If you encounter a bug, or you have any question, do not hesitate to open an issue in the repository.
