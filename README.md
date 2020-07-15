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

To inspect how jsql is parsing the input data, use the `describe` command to print the inferred schema of the data:

```bash
echo '[{"id": 1}, {"id": 2}]' | jsql describe

┌────────────────────────┐
│ fields                 │
├────────────────────────┤
│ Record                 │
│  - _ROW_UUID : VARCHAR │
│  - id : DOUBLE         │
│                        │
└────────────────────────┘
```

`_ROW_UUID` is a generated field.

Then, to query the data:

```bash
echo '[{"id": 1}, {"id": 2}]' | jsql query 'SELECT id FROM data'

┌─────┐
│ id  │
├─────┤
│ 1.0 │
├─────┤
│ 2.0 │
└─────┘
```

If you expect streaming data (one json per line), use the `--streaming` option:

```bash
curl -s  https://stream.wikimedia.org/v2/stream/recentchange \
    | grep data \
    | grep '/mediawiki/recentchange/1.0.0' \
    | sed 's/^data: //g' \
    | jsql --streaming --take 5 query "SELECT id, title, server_name FROM data WHERE server_name = 'www.wikidata.org'"

┌───────────────┬──────────────────────────────────────────────────┬───────────────────────┐
│ id            │ title                                            │ server_name           │
├───────────────┼──────────────────────────────────────────────────┼───────────────────────┤
│ 4.1684978E8   │ Catégorie:Page dont la protection est à vérifier │ fr.wikipedia.org      │
├───────────────┼──────────────────────────────────────────────────┼───────────────────────┤
│ 1.639128E7    │ Категория:Страницы с графиками                   │ ru.wikinews.org       │
├───────────────┼──────────────────────────────────────────────────┼───────────────────────┤
│ 1.41757828E9  │ Category:Lassen Peak                             │ commons.wikimedia.org │
├───────────────┼──────────────────────────────────────────────────┼───────────────────────┤
│ 1.281655368E9 │ Gustave (crocodile)                              │ en.wikipedia.org      │
├───────────────┼──────────────────────────────────────────────────┼───────────────────────┤
│ 4.16849781E8  │ Element (logiciel)                               │ fr.wikipedia.org      │
└───────────────┴──────────────────────────────────────────────────┴───────────────────────┘
```

## Other examples

Contributors of the most recent commits in the kubernetes repository:

```bash
http https://api.github.com/repos/kubernetes/kubernetes/commits \
    | jsql query 'SELECT data.author.login, COUNT(*) as total FROM data GROUP BY data.author.login'

┌───────────────┬───────┐
│ login         │ total │
├───────────────┼───────┤
│ hh            │ 1.0   │
├───────────────┼───────┤
│ MikeSpreitzer │ 1.0   │
├───────────────┼───────┤
│ giuseppe      │ 1.0   │
├───────────────┼───────┤
│ k8s-ci-robot  │ 25.0  │
├───────────────┼───────┤
│ rajansandeep  │ 1.0   │
└───────────────┴───────┘
```

Use [zoe](https://github.com/adevinta/zoe) to fetch the lags for a kafka consumer group and `jsql` to compute the total lag per topic:

```bash
zoe --silent -o json groups offsets my_group \
    | jsql query 'SELECT topic, SUM("lag") as total_lag FROM data GROUP BY topic'

┌─────────┬───────────┐
│ topic   │ total_lag │
├─────────┼───────────┤
│ topic_1 │ 470.0     │
│ topic_2 │ 470.0     │
└─────────┴───────────┘
```

## FAQ

### When accessing nested fields, I have "Table 'xxx' not found"!

Apache Calcite parser is a bit peaky. When accessing nested data using the `.` operator, you need to give the full path to your field including the table name. So:
- Instead of: `SELECT author.login FROM data`
- Use: `SELECT data.author.login FROM data`

### My query seems valid, but I get an error "Encountered "xxx" ... Was expecting one of ..."!

This is an Apache Calcite parse error. First check you didn't do any syntax error on your query.

One common issue that causes this problem is when you have a field that has the same name as a SQL keyword. In this case, you need to quote your field name with `"`. So:

- Instead of: `SELECT data.commit.author FROM data`
- Use: `SELECT data."commit".author FROM data`

Because `commit` is a Calcite SQL keyword, and clashes with the field named `commit`.

## Getting help

If you encounter a bug, or you have any question, do not hesitate to open an issue in the repository.
