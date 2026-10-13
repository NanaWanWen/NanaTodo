# YonErp · 采购订单管理系统

> 模拟企业级 ERP 采购流程的前后端分离练习项目，为学习用友 BIP / YonBIP 打基础。

## ✨ 功能特性

- 采购订单主从 CRUD
- 两级审批状态机（采购经理 → 总监）
- 供应商 / 物料基础数据管理
- 报表统计与多维度排行（供应商 / 采购员 / 物料 / 部门）
- 四角色权限控制（管理员 / 采购员 / 采购经理 / 总监）
- Redis 缓存、防重、限流、单号序列
- Docker Compose 一键启动

## 🏗️ 技术栈

| 层 | 选型 |
|---|---|
| 前端 | React 18 + TypeScript + Vite + Ant Design 5 + TailwindCSS + Recharts |
| 后端 | Java 17 + Spring Boot 3.2 + MyBatis-Plus + Redis |
| 数据库 | MySQL 8 |
| 缓存 | Redis 7 |

## 🚀 快速开始

前置条件：JDK 17+、Maven 3.8+、Node 18+、Docker

```bash
# 1. 启动 MySQL + Redis
docker compose up -d

# 2. 启动后端（端口 8080）
cd backend
mvn spring-boot:run

# 3. 启动前端（端口 5173）
cd frontend
npm install
npm run dev
