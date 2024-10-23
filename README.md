# MongoDB2Oracle

## 主要功能说明：

读取MongoDB数据库内的数据并生成Oracle的插入SQL，通过JDBC实现数据迁移。


## 程序使用说明：

1. 使用下面的语句向MongoDB插入包含各种数据类型的测试数据：

```
// 插入第一条数据
db.datatypetest.insertOne({  
    stringField: "Hello, MongoDB!",                 // 字符串类型  
    intField: 42,                                   // 整型  
    doubleField: 3.14159,                           // 浮点型  
    boolField: true,                                // 布尔型  
    dateField: new ISODate("2023-12-15T00:00:00Z"), // 日期型  
    arrayField: [1, 2, 3, 4, 5],                    // 数组类型  
    objectField: { name: "Alice", age: 30 },        // 对象类型  
    nullField: null,                                // 空值类型  
    binDataField: BinData(0, "Hello World"),        // 二进制数据类型，0表示通用二进制子类型  
    timestampField: Timestamp(12345678, 987654321) // 时间戳类型  
});  

// 插入第二条数据
db.datatypetest.insertOne({  
    stringField: "Another string",  
    intField: 100,  
    doubleField: 2.71828,  
    boolField: false,  
    dateField: new ISODate("2022-01-01T00:00:00Z"),  
    arrayField: ["apple", "banana", "cherry"],  
    objectField: { name: "Bob", age: 25 },  
    nullField: null,  
    binDataField: BinData(0, "Another binary data"),  
    timestampField: Timestamp(987654321, 12345678)
});  
  
// 插入第三条数据  
db.datatypetest.insertOne({  
    stringField: "Third string",  
    intField: -1,  
    doubleField: 0.0,  
    boolField: true,  
    dateField: new ISODate("1970-01-01T00:00:00Z"),  
    arrayField: [true, false, null, 123, "text"],  
    objectField: { name: "Charlie", age: 22 },  
    nullField: null,  
    binDataField: BinData(0, ""),  
    timestampField: Timestamp(0, 0)
});
```
----
2. 根据下面的表格完成MongoDB与Oracle之间的数据类型映射并在Oracle中创建Table： 

|MongoDB|Oracle|
|-|-|
|String|Varchar、Clob|
|Integer|Int、BigInt|
|Double|Double、Float、Number|
|Boolean|Boolean、Bit|
|Date|Date|
|Array|Varchar、Clob|
|Object|Varchar、Clob|
|Binary|Blob|
|Timestamp|Timestamp|


```sql
CREATE TABLE datatypetest
(
    stringField    Clob,
    intField       BigInt,
    doubleField    Float,
    boolField      Bit,
    dateField      DATE,
    arrayField     Varchar(200),
    objectField    Varchar(200),
    binDataField   BLOB,
    timestampField TIMESTAMP
);
```
----

3. 在Oracle中执行下面的SQL（注意将datatypetest替换成需要同步的表名），查询结果的每一行代表一张表的信息，第一个字段表示表名，其余字段表示列名，各字段之间用逗号分隔：

```sql
SELECT 
    table_name || ',' ||
    LISTAGG(column_name, ',') WITHIN GROUP (
    ORDER BY column_id) AS columns_list
FROM
    user_tab_columns
WHERE
    table_name = UPPER ('datatypetest')
GROUP BY
    table_name;

COLUMNS_LIST
---------------------------------------------------------------- 
DATATYPETEST,STRINGFIELD,INTFIELD,DOUBLEFIELD,BOOLFIELD,DATEFIELD,ARRAYFIELD,OBJECTFIELD,BINDATAFIELD,TIMESTAMPFIELD
```
----

4. 将查询结果写入到 migration.info 文件中。

----

5. 修改MongoDB2Oracle.java文件中的数据库连接信息：
   ```java
   //Oracle
   String oracleUrl = "jdbc:oracle:thin:@127.0.0.1:1521/orcl";
   String oracleUserName = "sys";
   String oraclePassword = "sys";

   //MongoDB连接信息
   String mongoDBUrl = "mongodb://127.0.0.1:1688:27017";
   String mongoDBUrlDatabase = "sys";
   ```
----

6. 运行程序，执行数据迁移
