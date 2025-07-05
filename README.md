# Java Raft 实现：一个高可用的分布式键值存储

这是一个从零开始、使用纯 Java 实现的 Raft 共识算法项目。该项目旨在深入理解分布式系统核心原理，并最终构建一个功能完备、高可用的分布式键值存储服务。

整个实现过程严格遵循 Diego Ongaro 的博士论文 ["In Search of an Understandable Consensus Algorithm"](https://raft.github.io/raft.pdf)，并解决了在实现过程中遇到的各种真实世界问题，如网络竞态、序列化、持久化和客户端交互等。

## 已实现功能

* **领导者选举 (Leader Election)**: 集群能够在节点启动或 Leader 宕机时，通过投票自动选举出唯一的 Leader。
* **心跳机制 (Heartbeat)**: Leader 通过周期性心跳维持其权威，防止 Follower 超时并重新发起选举。
* **日志复制 (Log Replication)**: Leader 能够接收客户端命令，将其作为日志条目，并可靠地复制到所有 Follower 节点。
* **日志一致性保证**: 实现了 Raft 的日志匹配原则，通过 `prevLogIndex` 和 `prevLogTerm` 进行一致性检查，并能在出现日志冲突时，由 Leader 强制同步 Follower 的日志。
* **多数派提交 (Majority Commit)**: 一个日志条目只有被集群中超过半数的节点复制后，才会被认为是“已提交”的。
* **状态机应用 (State Machine Apply)**: 已提交的日志中的命令会被自动应用到一个内存中的键值（Key-Value）状态机上，从而真正改变系统状态。
* **磁盘持久化 (Persistence)**:
    * Raft 的关键元数据（`currentTerm`, `votedFor`）被原子地写入磁盘。
    * Raft 的操作日志被持久化到磁盘文件中。
    * 系统能够在所有节点完全关闭并重启后，从磁盘恢复到之前的状态。
* **智能客户端 (Smart Client)**:
    * 提供一个交互式的命令行客户端 (`RaftClient`)。
    * 客户端能够自动处理 Leader 重定向。当请求发送到非 Leader 节点时，集群能引导客户端找到正确的 Leader。

## 技术栈

* **核心语言**: Java 17
* **构建工具**: Apache Maven
* **网络通信 (RPC)**: Netty
* **序列化**: Jackson
* **日志框架**: SLF4J + Logback

## 项目结构

我遵循“高内聚、低耦合”的原则，将项目划分为清晰的模块。

```
└── com.anzhi.raft
    ├── Main.java               // 服务器集群启动器
    │
    ├── client                  // 客户端相关
    │   ├── RaftClient.java     // 交互式 Shell 客户端
    │   ├── ClientRequest.java
    │   └── ClientResponse.java
    │
    ├── core                    // Raft 核心算法与模型
    │   ├── Node.java           // Raft 节点核心
    │   ├── NodeState.java
    │   ├── LogEntry.java
    │   └── Command.java
    │
    ├── rpc                     // RPC 网络通信相关
    │   ├── RpcClient.java
    │   ├── RpcServer.java
    │   └── ... (编解码器、消息体等)
    │
    ├── statemachine            // 状态机 (应用层)
    │   ├── StateMachine.java
    │   └── InMemoryStateMachine.java
    │
    └── storage                 // 持久化存储相关
        ├── LogModule.java
        ├── FileLogModule.java
        ├── PersistentState.java
        └── FileStorage.java
```

## 如何运行

### 前提条件

* JDK 17 或更高版本
* Apache Maven 3.6 或更高版本

### 1. 编译项目

在项目的根目录下（即包含 `pom.xml` 的目录），打开终端并运行以下命令。该命令会编译所有 Java 代码，并自动将所有依赖的 `.jar` 包复制到 `target/lib` 目录下。

```bash
mvn clean package
```

### 2. 启动 Raft 集群

编译成功后，在一个终端中运行 `Main` 类来启动一个 3 节点的 Raft 集群。

**Windows:**

```bash
java -cp "target/classes;target/lib/*" com.anzhi.raft.Main
```

**Linux / macOS:**

```bash
java -cp "target/classes:target/lib/*" com.anzhi.raft.Main
```

启动后，你将看到节点开始选举，并最终选举出一个 Leader。这个终端将保持运行，代表你的分布式服务正在后台运行。

### 3. 使用交互式客户端

**打开一个新的终端**，在同一个项目根目录下，运行 `RaftClient` 类。

**Windows:**

```bash
java -cp "target/classes;target/lib/*" com.anzhi.raft.RaftClient
```

**Linux / macOS:**

```bash
java -cp "target/classes:target/lib/*" com.anzhi.raft.RaftClient
```

启动后，你会看到一个命令行提示符 `raft-cli >`。现在你可以与你的分布式键值存储服务进行交互了！

**支持的命令:**

* `SET <key> <value>`: 设置一个键值对。
* `GET <key>`: 获取一个键的值。
* `exit` 或 `quit`: 退出客户端。

**示例:**

```
raft-cli > SET name "My Raft"
OK

raft-cli > GET name
My Raft

raft-cli > exit
Goodbye!
```

## 未来工作

* [ ] **日志压缩 (Snapshotting)**: 实现快照机制以防止日志无限增长。
* [ ] **集群成员变更**: 实现动态、安全地增加或移除集群节点的功能。
* [ ] **线性一致性读**: 优化 `GET` 命令，使其可以在 Follower 节点上被安全地处理，以分担 Leader 的读取压力。
