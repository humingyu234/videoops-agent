-- MySQL dump 10.13  Distrib 8.4.9, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: ai_video_test
-- ------------------------------------------------------
-- Server version	8.4.9

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `ai_video_test`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `ai_video_test` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `ai_video_test`;

--
-- Table structure for table `app_auth_client`
--

DROP TABLE IF EXISTS `app_auth_client`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_auth_client` (
  `id` bigint NOT NULL COMMENT '认证客户端 ID',
  `client_id` varchar(64) NOT NULL COMMENT '客户端标识',
  `client_key` varchar(64) NOT NULL COMMENT '客户端键',
  `client_secret_hash` varchar(100) DEFAULT NULL COMMENT '客户端密钥摘要',
  `grant_types` varchar(500) NOT NULL COMMENT '允许授权类型',
  `access_paths` varchar(1000) NOT NULL COMMENT '允许访问路径',
  `ip_whitelist` varchar(1000) DEFAULT NULL COMMENT 'IP 白名单',
  `token_timeout` bigint NOT NULL COMMENT '令牌固定超时秒数',
  `active_timeout` bigint NOT NULL COMMENT '令牌活跃超时秒数',
  `client_revision` bigint NOT NULL DEFAULT '1' COMMENT '客户端修订号',
  `status` varchar(16) NOT NULL DEFAULT 'active' COMMENT '状态',
  `created_by_type` varchar(16) NOT NULL COMMENT '创建主体类型',
  `created_by_id` bigint NOT NULL COMMENT '创建主体 ID',
  `updated_by_type` varchar(16) NOT NULL COMMENT '更新主体类型',
  `updated_by_id` bigint NOT NULL COMMENT '更新主体 ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_auth_client_client_id` (`client_id`),
  UNIQUE KEY `uk_app_auth_client_client_key` (`client_key`),
  CONSTRAINT `ck_app_auth_client_actor_types` CHECK (((`created_by_type` in (_utf8mb4'app_user',_utf8mb4'sys_user')) and (`updated_by_type` in (_utf8mb4'app_user',_utf8mb4'sys_user')))),
  CONSTRAINT `ck_app_auth_client_revision` CHECK ((`client_revision` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='创作端认证客户端表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `app_auth_client`
--

LOCK TABLES `app_auth_client` WRITE;
/*!40000 ALTER TABLE `app_auth_client` DISABLE KEYS */;
/*!40000 ALTER TABLE `app_auth_client` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `app_login_log`
--

DROP TABLE IF EXISTS `app_login_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_login_log` (
  `login_log_id` bigint NOT NULL COMMENT '登录日志 ID',
  `auth_method` varchar(32) NOT NULL COMMENT '认证方式',
  `masked_identifier` varchar(128) NOT NULL COMMENT '脱敏标识',
  `client_id` varchar(64) NOT NULL COMMENT '客户端标识',
  `result_code` int NOT NULL COMMENT '结果编码',
  `failure_category` varchar(32) DEFAULT NULL COMMENT '失败分类',
  `user_id` bigint DEFAULT NULL COMMENT '创作端用户 ID',
  `session_id` varchar(128) DEFAULT NULL COMMENT '会话 ID',
  `ip_address` varchar(64) NOT NULL COMMENT 'IP 地址',
  `device_summary` varchar(255) DEFAULT NULL COMMENT '设备摘要',
  `request_id` varchar(64) NOT NULL COMMENT '请求 ID',
  `occurred_at` datetime NOT NULL COMMENT '发生时间',
  PRIMARY KEY (`login_log_id`),
  KEY `idx_app_login_user_time` (`user_id`,`occurred_at`),
  KEY `idx_app_login_request` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='创作端登录日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `app_login_log`
--

LOCK TABLES `app_login_log` WRITE;
/*!40000 ALTER TABLE `app_login_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `app_login_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `app_permission`
--

DROP TABLE IF EXISTS `app_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_permission` (
  `permission_id` bigint NOT NULL COMMENT '权限 ID',
  `permission_code` varchar(100) NOT NULL COMMENT '权限编码',
  `permission_name` varchar(100) NOT NULL COMMENT '权限名称',
  `resource_type` varchar(32) NOT NULL COMMENT '资源类型',
  `action` varchar(32) NOT NULL COMMENT '操作类型',
  `permission_revision` bigint NOT NULL DEFAULT '1' COMMENT '权限修订号',
  `status` varchar(16) NOT NULL DEFAULT 'active' COMMENT '状态',
  `created_by_type` varchar(16) NOT NULL COMMENT '创建主体类型',
  `created_by_id` bigint NOT NULL COMMENT '创建主体 ID',
  `updated_by_type` varchar(16) NOT NULL COMMENT '更新主体类型',
  `updated_by_id` bigint NOT NULL COMMENT '更新主体 ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`permission_id`),
  UNIQUE KEY `uk_app_permission_code` (`permission_code`),
  CONSTRAINT `ck_app_permission_actor_types` CHECK (((`created_by_type` in (_utf8mb4'app_user',_utf8mb4'sys_user')) and (`updated_by_type` in (_utf8mb4'app_user',_utf8mb4'sys_user')))),
  CONSTRAINT `ck_app_permission_revision` CHECK ((`permission_revision` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='创作端权限表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `app_permission`
--

LOCK TABLES `app_permission` WRITE;
/*!40000 ALTER TABLE `app_permission` DISABLE KEYS */;
INSERT INTO `app_permission` VALUES (1000001,'aivideo:studio:query','工作台查看','studio','query',1,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:22','2026-08-08 14:45:22'),(1000002,'aivideo:studio:create','工作台创建','studio','create',1,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:22','2026-08-08 14:45:22'),(1000003,'aivideo:studio:edit','工作台编辑','studio','edit',1,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:22','2026-08-08 14:45:22'),(1000004,'aivideo:studio:generate','工作台生成','studio','generate',1,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:22','2026-08-08 14:45:22'),(1000005,'aivideo:script:query','脚本查看','script','query',1,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:22','2026-08-08 14:45:22'),(1000006,'aivideo:script:edit','脚本编辑','script','edit',1,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:22','2026-08-08 14:45:22'),(1000007,'aivideo:script:confirm','脚本确认','script','confirm',1,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:22','2026-08-08 14:45:22'),(1000008,'aivideo:script:remove','脚本删除','script','remove',1,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:22','2026-08-08 14:45:22'),(1000009,'aivideo:task:query','任务查看','task','query',1,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:22','2026-08-08 14:45:22'),(1000010,'aivideo:task:cancel','任务取消','task','cancel',1,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:22','2026-08-08 14:45:22'),(1000011,'aivideo:quota:query','额度查看','quota','query',1,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:22','2026-08-08 14:45:22'),(1000012,'aivideo:quota:use','额度使用','quota','use',1,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:22','2026-08-08 14:45:22'),(1000013,'aivideo:quota:organization-query','组织额度查看','quota','organization-query',1,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:22','2026-08-08 14:45:22'),(1000014,'aivideo:notification:query','通知查看','notification','query',1,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:22','2026-08-08 14:45:22'),(1000015,'aivideo:notification:edit','通知编辑','notification','edit',1,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:22','2026-08-08 14:45:22'),(1000016,'aivideo:portrait:query','人物形象查看','portrait','query',1,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:24','2026-08-08 14:45:24'),(1000017,'aivideo:portrait:add','人物形象创建','portrait','add',1,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:24','2026-08-08 14:45:24'),(1000018,'aivideo:portrait:edit','人物形象编辑','portrait','edit',1,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:24','2026-08-08 14:45:24'),(1000019,'aivideo:portrait:remove','人物形象删除','portrait','remove',1,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:24','2026-08-08 14:45:24'),(1000020,'aivideo:voice:query','声音查看','voice','query',1,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:25','2026-08-08 14:45:25'),(1000021,'aivideo:voice:upload','声音上传','voice','upload',1,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:25','2026-08-08 14:45:25'),(1000022,'aivideo:voice:edit','声音文本修改','voice','edit',1,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:25','2026-08-08 14:45:25'),(1000023,'aivideo:voice:transcribe','声音转写重试','voice','transcribe',1,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:25','2026-08-08 14:45:25'),(1000024,'aivideo:voice:delete','声音删除','voice','delete',1,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:25','2026-08-08 14:45:25');
/*!40000 ALTER TABLE `app_permission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `app_role`
--

DROP TABLE IF EXISTS `app_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_role` (
  `role_id` bigint NOT NULL COMMENT '角色 ID',
  `role_code` varchar(64) NOT NULL COMMENT '角色编码',
  `role_name` varchar(64) NOT NULL COMMENT '角色名称',
  `scope_type` varchar(16) NOT NULL COMMENT '作用域类型',
  `built_in` tinyint NOT NULL DEFAULT '0' COMMENT '是否内置角色',
  `role_revision` bigint NOT NULL DEFAULT '1' COMMENT '角色修订号',
  `status` varchar(16) NOT NULL DEFAULT 'active' COMMENT '状态',
  `created_by_type` varchar(16) NOT NULL COMMENT '创建主体类型',
  `created_by_id` bigint NOT NULL COMMENT '创建主体 ID',
  `updated_by_type` varchar(16) NOT NULL COMMENT '更新主体类型',
  `updated_by_id` bigint NOT NULL COMMENT '更新主体 ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`role_id`),
  UNIQUE KEY `uk_app_role_role_code` (`role_code`),
  CONSTRAINT `ck_app_role_actor_types` CHECK (((`created_by_type` in (_utf8mb4'app_user',_utf8mb4'sys_user')) and (`updated_by_type` in (_utf8mb4'app_user',_utf8mb4'sys_user')))),
  CONSTRAINT `ck_app_role_revision` CHECK ((`role_revision` > 0)),
  CONSTRAINT `ck_app_role_scope_type` CHECK ((`scope_type` in (_utf8mb4'personal',_utf8mb4'organization')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='创作端角色表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `app_role`
--

LOCK TABLES `app_role` WRITE;
/*!40000 ALTER TABLE `app_role` DISABLE KEYS */;
INSERT INTO `app_role` VALUES (1000101,'personal_creator','个人创作者','personal',1,2,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:22','2026-08-08 14:45:25','0'),(1000102,'organization_owner','组织所有者','organization',1,1,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:22','2026-08-08 14:45:22','0'),(1000103,'organization_admin','组织管理员','organization',1,1,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:22','2026-08-08 14:45:22','0'),(1000104,'organization_member','组织成员','organization',1,1,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:22','2026-08-08 14:45:22','0');
/*!40000 ALTER TABLE `app_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `app_role_permission`
--

DROP TABLE IF EXISTS `app_role_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_role_permission` (
  `id` bigint NOT NULL COMMENT '角色权限关联 ID',
  `role_id` bigint NOT NULL COMMENT '角色 ID',
  `permission_id` bigint NOT NULL COMMENT '权限 ID',
  `status` varchar(16) NOT NULL DEFAULT 'active' COMMENT '状态',
  `created_by_type` varchar(16) NOT NULL COMMENT '创建主体类型',
  `created_by_id` bigint NOT NULL COMMENT '创建主体 ID',
  `updated_by_type` varchar(16) NOT NULL COMMENT '更新主体类型',
  `updated_by_id` bigint NOT NULL COMMENT '更新主体 ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_role_permission_role_permission` (`role_id`,`permission_id`),
  KEY `fk_app_role_permission_permission` (`permission_id`),
  CONSTRAINT `fk_app_role_permission_permission` FOREIGN KEY (`permission_id`) REFERENCES `app_permission` (`permission_id`),
  CONSTRAINT `fk_app_role_permission_role` FOREIGN KEY (`role_id`) REFERENCES `app_role` (`role_id`),
  CONSTRAINT `ck_app_role_permission_actor_types` CHECK (((`created_by_type` in (_utf8mb4'app_user',_utf8mb4'sys_user')) and (`updated_by_type` in (_utf8mb4'app_user',_utf8mb4'sys_user'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='创作端角色权限关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `app_role_permission`
--

LOCK TABLES `app_role_permission` WRITE;
/*!40000 ALTER TABLE `app_role_permission` DISABLE KEYS */;
INSERT INTO `app_role_permission` VALUES (1000216,1000101,1000016,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:24','2026-08-08 14:45:24'),(1000217,1000101,1000017,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:24','2026-08-08 14:45:24'),(1000218,1000101,1000018,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:24','2026-08-08 14:45:24'),(1000219,1000101,1000019,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:24','2026-08-08 14:45:24'),(1000220,1000101,1000020,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:25','2026-08-08 14:45:25'),(1000221,1000101,1000021,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:25','2026-08-08 14:45:25'),(1000222,1000101,1000022,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:25','2026-08-08 14:45:25'),(1000223,1000101,1000023,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:25','2026-08-08 14:45:25'),(1000224,1000101,1000024,'active','sys_user',1761100000000000001,'sys_user',1761100000000000001,'2026-08-08 14:45:25','2026-08-08 14:45:25');
/*!40000 ALTER TABLE `app_role_permission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `app_security_audit`
--

DROP TABLE IF EXISTS `app_security_audit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_security_audit` (
  `audit_id` bigint NOT NULL COMMENT '安全审计 ID',
  `resource_type` varchar(64) NOT NULL COMMENT '资源类型',
  `resource_id` varchar(64) NOT NULL COMMENT '资源 ID',
  `action` varchar(64) NOT NULL COMMENT '操作',
  `actor_type` varchar(16) NOT NULL COMMENT '主体类型',
  `actor_id` bigint NOT NULL COMMENT '主体 ID',
  `before_digest` varchar(128) DEFAULT NULL COMMENT '变更前摘要',
  `after_digest` varchar(128) DEFAULT NULL COMMENT '变更后摘要',
  `reason` varchar(500) NOT NULL COMMENT '原因',
  `request_id` varchar(64) NOT NULL COMMENT '请求 ID',
  `ip_address` varchar(64) NOT NULL COMMENT 'IP 地址',
  `occurred_at` datetime NOT NULL COMMENT '发生时间',
  PRIMARY KEY (`audit_id`),
  KEY `idx_app_audit_resource` (`resource_type`,`resource_id`,`occurred_at`),
  KEY `idx_app_audit_actor` (`actor_type`,`actor_id`,`occurred_at`),
  CONSTRAINT `ck_app_security_audit_actor_type` CHECK ((`actor_type` in (_utf8mb4'app_user',_utf8mb4'sys_user')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='创作端安全审计表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `app_security_audit`
--

LOCK TABLES `app_security_audit` WRITE;
/*!40000 ALTER TABLE `app_security_audit` DISABLE KEYS */;
/*!40000 ALTER TABLE `app_security_audit` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `app_social_identity`
--

DROP TABLE IF EXISTS `app_social_identity`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_social_identity` (
  `social_identity_id` bigint NOT NULL COMMENT '第三方身份 ID',
  `user_id` bigint NOT NULL COMMENT '创作端用户 ID',
  `provider` varchar(32) NOT NULL COMMENT '第三方提供方',
  `provider_subject` varchar(128) NOT NULL COMMENT '第三方主体标识',
  `status` varchar(16) NOT NULL DEFAULT 'active' COMMENT '状态',
  `created_by_type` varchar(16) NOT NULL COMMENT '创建主体类型',
  `created_by_id` bigint NOT NULL COMMENT '创建主体 ID',
  `updated_by_type` varchar(16) NOT NULL COMMENT '更新主体类型',
  `updated_by_id` bigint NOT NULL COMMENT '更新主体 ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`social_identity_id`),
  UNIQUE KEY `uk_app_social_identity_provider_subject` (`provider`,`provider_subject`),
  UNIQUE KEY `uk_app_social_identity_user_provider` (`user_id`,`provider`),
  CONSTRAINT `fk_app_social_identity_user` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`user_id`),
  CONSTRAINT `ck_app_social_identity_actor_types` CHECK (((`created_by_type` in (_utf8mb4'app_user',_utf8mb4'sys_user')) and (`updated_by_type` in (_utf8mb4'app_user',_utf8mb4'sys_user'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='创作端第三方身份表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `app_social_identity`
--

LOCK TABLES `app_social_identity` WRITE;
/*!40000 ALTER TABLE `app_social_identity` DISABLE KEYS */;
/*!40000 ALTER TABLE `app_social_identity` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `app_user`
--

DROP TABLE IF EXISTS `app_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_user` (
  `user_id` bigint NOT NULL COMMENT '创作端用户 ID',
  `username` varchar(64) NOT NULL COMMENT '用户名',
  `username_normalized` varchar(64) NOT NULL COMMENT '标准化用户名',
  `password_hash` varchar(100) NOT NULL COMMENT '密码摘要',
  `phone_normalized` varchar(32) DEFAULT NULL COMMENT '标准化手机号',
  `email_normalized` varchar(128) DEFAULT NULL COMMENT '标准化邮箱',
  `personal_tenant_id` bigint NOT NULL COMMENT '个人租户 ID',
  `display_name` varchar(64) NOT NULL COMMENT '显示名称',
  `status` varchar(16) NOT NULL DEFAULT 'active' COMMENT '状态',
  `must_change_password` tinyint NOT NULL DEFAULT '0' COMMENT '是否必须修改密码',
  `credential_revision` bigint NOT NULL DEFAULT '1' COMMENT '凭据修订号',
  `identity_revision` bigint NOT NULL DEFAULT '1' COMMENT '身份修订号',
  `permission_revision` bigint NOT NULL DEFAULT '1' COMMENT '权限修订号',
  `created_by_type` varchar(16) NOT NULL COMMENT '创建主体类型',
  `created_by_id` bigint NOT NULL COMMENT '创建主体 ID',
  `updated_by_type` varchar(16) NOT NULL COMMENT '更新主体类型',
  `updated_by_id` bigint NOT NULL COMMENT '更新主体 ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_app_user_username_normalized` (`username_normalized`),
  UNIQUE KEY `uk_app_user_personal_tenant_id` (`personal_tenant_id`),
  UNIQUE KEY `uk_app_user_phone_normalized` (`phone_normalized`),
  UNIQUE KEY `uk_app_user_email_normalized` (`email_normalized`),
  CONSTRAINT `ck_app_user_actor_types` CHECK (((`created_by_type` in (_utf8mb4'app_user',_utf8mb4'sys_user')) and (`updated_by_type` in (_utf8mb4'app_user',_utf8mb4'sys_user'))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='创作端用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `app_user`
--

LOCK TABLES `app_user` WRITE;
/*!40000 ALTER TABLE `app_user` DISABLE KEYS */;
/*!40000 ALTER TABLE `app_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `app_user_role`
--

DROP TABLE IF EXISTS `app_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_user_role` (
  `id` bigint NOT NULL COMMENT '用户角色关联 ID',
  `user_id` bigint NOT NULL COMMENT '创作端用户 ID',
  `role_id` bigint NOT NULL COMMENT '角色 ID',
  `status` varchar(16) NOT NULL DEFAULT 'active' COMMENT '状态',
  `valid_from` datetime DEFAULT NULL COMMENT '生效时间',
  `valid_until` datetime DEFAULT NULL COMMENT '失效时间',
  `created_by_type` varchar(16) NOT NULL COMMENT '创建主体类型',
  `created_by_id` bigint NOT NULL COMMENT '创建主体 ID',
  `updated_by_type` varchar(16) NOT NULL COMMENT '更新主体类型',
  `updated_by_id` bigint NOT NULL COMMENT '更新主体 ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_user_role_user_role` (`user_id`,`role_id`),
  KEY `fk_app_user_role_role` (`role_id`),
  CONSTRAINT `fk_app_user_role_role` FOREIGN KEY (`role_id`) REFERENCES `app_role` (`role_id`),
  CONSTRAINT `fk_app_user_role_user` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`user_id`),
  CONSTRAINT `ck_app_user_role_actor_types` CHECK (((`created_by_type` in (_utf8mb4'app_user',_utf8mb4'sys_user')) and (`updated_by_type` in (_utf8mb4'app_user',_utf8mb4'sys_user')))),
  CONSTRAINT `ck_app_user_role_validity` CHECK (((`valid_from` is null) or (`valid_until` is null) or (`valid_until` > `valid_from`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='创作端用户角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `app_user_role`
--

LOCK TABLES `app_user_role` WRITE;
/*!40000 ALTER TABLE `app_user_role` DISABLE KEYS */;
/*!40000 ALTER TABLE `app_user_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `av_asset`
--

DROP TABLE IF EXISTS `av_asset`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `av_asset` (
  `asset_id` bigint NOT NULL COMMENT '素材 ID',
  `tenant_id` bigint NOT NULL COMMENT '租户 ID',
  `workspace_id` varchar(128) NOT NULL COMMENT '工作区稳定键',
  `owner_id` bigint NOT NULL COMMENT 'app 用户 ID',
  `category` varchar(32) NOT NULL COMMENT '素材分类',
  `object_key` varchar(512) NOT NULL COMMENT '私有对象 Key',
  `original_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `content_type` varchar(64) NOT NULL COMMENT '服务端确认 MIME',
  `file_format` varchar(16) NOT NULL COMMENT '服务端确认格式',
  `width` int NOT NULL COMMENT '宽度',
  `height` int NOT NULL COMMENT '高度',
  `file_size` bigint NOT NULL COMMENT '字节数',
  `status` varchar(16) NOT NULL COMMENT 'ready/failed',
  `failure_reason` varchar(500) DEFAULT NULL COMMENT '失败原因',
  `create_dept` bigint DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`asset_id`),
  UNIQUE KEY `uk_av_asset_object_key` (`object_key`),
  KEY `idx_av_asset_owner` (`tenant_id`,`workspace_id`,`owner_id`,`del_flag`,`create_time`),
  CONSTRAINT `ck_av_asset_portrait_type` CHECK (((`category` <> _utf8mb4'portrait_image') or ((`file_format` in (_utf8mb4'jpeg',_utf8mb4'png')) and (`file_size` <= 10485760))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 视频私有素材表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `av_asset`
--

LOCK TABLES `av_asset` WRITE;
/*!40000 ALTER TABLE `av_asset` DISABLE KEYS */;
/*!40000 ALTER TABLE `av_asset` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `av_portrait`
--

DROP TABLE IF EXISTS `av_portrait`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `av_portrait` (
  `portrait_id` bigint NOT NULL COMMENT '人物形象 ID',
  `tenant_id` bigint NOT NULL COMMENT '租户 ID',
  `workspace_id` varchar(128) NOT NULL COMMENT '工作区稳定键',
  `owner_id` bigint NOT NULL COMMENT 'app 用户 ID',
  `asset_id` bigint NOT NULL COMMENT '唯一图片素材 ID',
  `name` varchar(80) NOT NULL COMMENT '形象名称',
  `gender` varchar(16) NOT NULL DEFAULT 'unspecified' COMMENT 'female/male/unspecified',
  `scene_tags_json` json NOT NULL COMMENT '场景标签',
  `note` varchar(500) DEFAULT NULL COMMENT '备注',
  `record_revision` bigint NOT NULL DEFAULT '1' COMMENT '乐观锁修订',
  `create_dept` bigint DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`portrait_id`),
  UNIQUE KEY `uk_av_portrait_asset` (`asset_id`),
  KEY `idx_av_portrait_owner` (`tenant_id`,`workspace_id`,`owner_id`,`del_flag`,`create_time`),
  CONSTRAINT `fk_av_portrait_asset` FOREIGN KEY (`asset_id`) REFERENCES `av_asset` (`asset_id`),
  CONSTRAINT `ck_av_portrait_gender` CHECK ((`gender` in (_utf8mb4'female',_utf8mb4'male',_utf8mb4'unspecified'))),
  CONSTRAINT `ck_av_portrait_revision` CHECK ((`record_revision` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户人物形象表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `av_portrait`
--

LOCK TABLES `av_portrait` WRITE;
/*!40000 ALTER TABLE `av_portrait` DISABLE KEYS */;
/*!40000 ALTER TABLE `av_portrait` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `av_voice`
--

DROP TABLE IF EXISTS `av_voice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `av_voice` (
  `voice_id` bigint NOT NULL COMMENT '声音 ID',
  `tenant_id` bigint NOT NULL COMMENT '租户 ID',
  `workspace_id` varchar(128) NOT NULL COMMENT '工作区稳定键',
  `owner_id` bigint NOT NULL COMMENT 'app 用户 ID',
  `asset_id` bigint NOT NULL COMMENT '唯一音频素材 ID',
  `idempotency_key` varchar(128) NOT NULL COMMENT '客户端幂等键',
  `upload_fingerprint` char(64) NOT NULL COMMENT '文件及元数据摘要',
  `voice_type` varchar(16) NOT NULL DEFAULT 'origin' COMMENT 'origin/clone/public',
  `name` varchar(80) NOT NULL COMMENT '声音名称',
  `gender` varchar(16) NOT NULL DEFAULT 'unspecified' COMMENT 'female/male/unspecified',
  `style` varchar(40) DEFAULT NULL COMMENT '声音风格',
  `tags_json` json NOT NULL COMMENT '标签 JSON 数组',
  `note` varchar(500) DEFAULT NULL COMMENT '备注',
  `transcript_text` text COMMENT '转写或人工修正文本',
  `detected_language` varchar(16) DEFAULT NULL COMMENT '识别语言',
  `duration_millis` bigint DEFAULT NULL COMMENT '音频时长（毫秒）',
  `transcription_status` varchar(16) NOT NULL DEFAULT 'pending' COMMENT 'pending/transcribing/ready/failed',
  `failure_code` varchar(64) DEFAULT NULL COMMENT '稳定失败标识',
  `failure_message` varchar(500) DEFAULT NULL COMMENT '脱敏失败说明',
  `attempt_count` int NOT NULL DEFAULT '0' COMMENT '自动尝试次数',
  `next_attempt_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次领取时间',
  `lease_owner` varchar(128) DEFAULT NULL COMMENT '处理租约持有者',
  `lease_expires_at` datetime DEFAULT NULL COMMENT '处理租约过期时间',
  `record_revision` bigint NOT NULL DEFAULT '1' COMMENT '乐观并发修订号',
  `create_dept` bigint DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag` char(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`voice_id`),
  UNIQUE KEY `uk_av_voice_owner_idempotency` (`tenant_id`,`owner_id`,`idempotency_key`),
  UNIQUE KEY `uk_av_voice_tenant_asset` (`tenant_id`,`asset_id`),
  KEY `idx_av_voice_owner_list` (`tenant_id`,`workspace_id`,`owner_id`,`del_flag`,`create_time`,`voice_id`),
  KEY `idx_av_voice_transcription_claim` (`transcription_status`,`next_attempt_at`,`lease_expires_at`),
  KEY `fk_av_voice_asset` (`asset_id`),
  CONSTRAINT `fk_av_voice_asset` FOREIGN KEY (`asset_id`) REFERENCES `av_asset` (`asset_id`),
  CONSTRAINT `av_voice_chk_1` CHECK ((`transcription_status` in (_utf8mb4'pending',_utf8mb4'transcribing',_utf8mb4'ready',_utf8mb4'failed'))),
  CONSTRAINT `ck_av_voice_attempt` CHECK ((`attempt_count` >= 0)),
  CONSTRAINT `ck_av_voice_duration` CHECK (((`duration_millis` is null) or (`duration_millis` >= 0))),
  CONSTRAINT `ck_av_voice_gender` CHECK ((`gender` in (_utf8mb4'female',_utf8mb4'male',_utf8mb4'unspecified'))),
  CONSTRAINT `ck_av_voice_revision` CHECK ((`record_revision` > 0)),
  CONSTRAINT `ck_av_voice_transcription_status` CHECK ((`transcription_status` in (_utf8mb4'pending',_utf8mb4'transcribing',_utf8mb4'ready',_utf8mb4'failed'))),
  CONSTRAINT `ck_av_voice_type` CHECK ((`voice_type` in (_utf8mb4'origin',_utf8mb4'clone',_utf8mb4'public')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户声音资源表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `av_voice`
--

LOCK TABLES `av_voice` WRITE;
/*!40000 ALTER TABLE `av_voice` DISABLE KEYS */;
/*!40000 ALTER TABLE `av_voice` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `gen_table`
--

DROP TABLE IF EXISTS `gen_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gen_table` (
  `table_id` bigint NOT NULL COMMENT '编号',
  `data_name` varchar(200) DEFAULT '' COMMENT '数据源名称',
  `table_name` varchar(200) DEFAULT '' COMMENT '表名称',
  `table_comment` varchar(500) DEFAULT '' COMMENT '表描述',
  `class_name` varchar(100) DEFAULT '' COMMENT '实体类名称',
  `tpl_category` varchar(200) DEFAULT 'crud' COMMENT '使用的模板（crud单表操作 tree树表操作）',
  `frontend_type` varchar(50) DEFAULT 'vue' COMMENT '前端模板类型，对应 vm 下的模板目录',
  `package_name` varchar(100) DEFAULT NULL COMMENT '生成包路径',
  `module_name` varchar(30) DEFAULT NULL COMMENT '生成模块名',
  `business_name` varchar(30) DEFAULT NULL COMMENT '生成业务名',
  `function_name` varchar(50) DEFAULT NULL COMMENT '生成功能名',
  `function_author` varchar(50) DEFAULT NULL COMMENT '生成功能作者',
  `gen_type` char(1) DEFAULT '0' COMMENT '生成代码方式（0zip压缩包 1自定义路径）',
  `gen_path` varchar(200) DEFAULT '/' COMMENT '生成路径（不填默认项目路径）',
  `options` varchar(1000) DEFAULT NULL COMMENT '其它生成选项',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`table_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='代码生成业务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gen_table`
--

LOCK TABLES `gen_table` WRITE;
/*!40000 ALTER TABLE `gen_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `gen_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `gen_table_column`
--

DROP TABLE IF EXISTS `gen_table_column`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gen_table_column` (
  `column_id` bigint NOT NULL COMMENT '编号',
  `table_id` bigint DEFAULT NULL COMMENT '归属表编号',
  `column_name` varchar(200) DEFAULT NULL COMMENT '列名称',
  `column_comment` varchar(500) DEFAULT NULL COMMENT '列描述',
  `column_type` varchar(100) DEFAULT NULL COMMENT '列类型',
  `java_type` varchar(500) DEFAULT NULL COMMENT 'JAVA类型',
  `java_field` varchar(200) DEFAULT NULL COMMENT 'JAVA字段名',
  `is_pk` char(1) DEFAULT NULL COMMENT '是否主键（1是）',
  `is_increment` char(1) DEFAULT NULL COMMENT '是否自增（1是）',
  `is_required` char(1) DEFAULT NULL COMMENT '是否必填（1是）',
  `is_insert` char(1) DEFAULT NULL COMMENT '是否为插入字段（1是）',
  `is_edit` char(1) DEFAULT NULL COMMENT '是否编辑字段（1是）',
  `is_list` char(1) DEFAULT NULL COMMENT '是否列表字段（1是）',
  `is_query` char(1) DEFAULT NULL COMMENT '是否查询字段（1是）',
  `query_type` varchar(200) DEFAULT 'EQ' COMMENT '查询方式（等于、不等于、大于、小于、范围）',
  `html_type` varchar(200) DEFAULT NULL COMMENT '显示类型（文本框、文本域、下拉框、复选框、单选框、日期控件）',
  `dict_type` varchar(200) DEFAULT '' COMMENT '字典类型',
  `sort` int DEFAULT NULL COMMENT '排序',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`column_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='代码生成业务表字段';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gen_table_column`
--

LOCK TABLES `gen_table_column` WRITE;
/*!40000 ALTER TABLE `gen_table_column` DISABLE KEYS */;
/*!40000 ALTER TABLE `gen_table_column` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_client`
--

DROP TABLE IF EXISTS `sys_client`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_client` (
  `id` bigint NOT NULL COMMENT 'id',
  `client_id` varchar(64) DEFAULT NULL COMMENT '客户端id',
  `client_key` varchar(32) DEFAULT NULL COMMENT '客户端key',
  `client_secret` varchar(255) DEFAULT NULL COMMENT '客户端秘钥',
  `grant_type` varchar(255) DEFAULT NULL COMMENT '授权类型',
  `device_type` varchar(32) DEFAULT NULL COMMENT '设备类型',
  `access_path` varchar(2000) DEFAULT NULL COMMENT '允许访问路径',
  `ip_whitelist` varchar(1000) DEFAULT NULL COMMENT 'IP白名单',
  `active_timeout` int DEFAULT '1800' COMMENT 'token活跃超时时间',
  `timeout` int DEFAULT '604800' COMMENT 'token固定超时',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0代表存在 1代表删除）',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统授权表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_client`
--

LOCK TABLES `sys_client` WRITE;
/*!40000 ALTER TABLE `sys_client` DISABLE KEYS */;
INSERT INTO `sys_client` VALUES (1762000000000000001,'e5cd7e4891bf95d1d19206ce24a7b32e','pc','pc123','password,social','pc',NULL,NULL,1800,604800,'0','0',1761000000000000103,1761100000000000001,'2026-08-08 14:45:10',1761100000000000001,'2026-08-08 14:45:10'),(1762000000000000002,'428a8310cd442757ae699df5d894f051','app','app123','password,sms,social','android','/app/**',NULL,1800,604800,'0','0',1761000000000000103,1761100000000000001,'2026-08-08 14:45:10',1761100000000000001,'2026-08-08 14:45:10');
/*!40000 ALTER TABLE `sys_client` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_config`
--

DROP TABLE IF EXISTS `sys_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_config` (
  `config_id` bigint NOT NULL COMMENT '参数主键',
  `config_name` varchar(100) DEFAULT '' COMMENT '参数名称',
  `config_key` varchar(100) DEFAULT '' COMMENT '参数键名',
  `config_value` varchar(500) DEFAULT '' COMMENT '参数键值',
  `config_type` char(1) DEFAULT 'N' COMMENT '系统内置（Y是 N否）',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='参数配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_config`
--

LOCK TABLES `sys_config` WRITE;
/*!40000 ALTER TABLE `sys_config` DISABLE KEYS */;
INSERT INTO `sys_config` VALUES (1761700000000000001,'用户管理-账号初始密码','sys.user.initPassword','123456','Y',1761000000000000103,1761100000000000001,'2026-08-08 14:45:03',NULL,NULL,'初始化密码 123456'),(1761700000000000002,'账号自助-是否开启用户注册功能','sys.account.registerUser','false','Y',1761000000000000103,1761100000000000001,'2026-08-08 14:45:03',NULL,NULL,'是否开启注册用户功能（true开启，false关闭）'),(1761700000000000003,'OSS预览列表资源开关','sys.oss.previewListResource','true','Y',1761000000000000103,1761100000000000001,'2026-08-08 14:45:03',NULL,NULL,'true:开启, false:关闭');
/*!40000 ALTER TABLE `sys_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dept`
--

DROP TABLE IF EXISTS `sys_dept`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dept` (
  `dept_id` bigint NOT NULL COMMENT '部门id',
  `parent_id` bigint DEFAULT '0' COMMENT '父部门id',
  `ancestors` varchar(500) DEFAULT '' COMMENT '祖级列表',
  `dept_name` varchar(30) DEFAULT '' COMMENT '部门名称',
  `dept_category` varchar(100) DEFAULT NULL COMMENT '部门类别编码',
  `order_num` int DEFAULT '0' COMMENT '显示顺序',
  `leader` bigint DEFAULT NULL COMMENT '负责人',
  `phone` varchar(11) DEFAULT NULL COMMENT '联系电话',
  `email` varchar(50) DEFAULT NULL COMMENT '邮箱',
  `status` char(1) DEFAULT '0' COMMENT '部门状态（0正常 1停用）',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0代表存在 1代表删除）',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`dept_id`),
  KEY `idx_sys_dept_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dept`
--

LOCK TABLES `sys_dept` WRITE;
/*!40000 ALTER TABLE `sys_dept` DISABLE KEYS */;
INSERT INTO `sys_dept` VALUES (1761000000000000100,0,'0','XXX科技',NULL,0,NULL,'15888888888','xxx@qq.com','0','0',1761000000000000103,1761100000000000001,'2026-08-08 14:44:41',NULL,NULL),(1761000000000000101,1761000000000000100,'0,1761000000000000100','深圳总公司',NULL,1,NULL,'15888888888','xxx@qq.com','0','0',1761000000000000103,1761100000000000001,'2026-08-08 14:44:42',NULL,NULL),(1761000000000000102,1761000000000000100,'0,1761000000000000100','长沙分公司',NULL,2,NULL,'15888888888','xxx@qq.com','0','0',1761000000000000103,1761100000000000001,'2026-08-08 14:44:42',NULL,NULL),(1761000000000000103,1761000000000000101,'0,1761000000000000100,1761000000000000101','研发部门',NULL,1,1761100000000000001,'15888888888','xxx@qq.com','0','0',1761000000000000103,1761100000000000001,'2026-08-08 14:44:42',NULL,NULL),(1761000000000000104,1761000000000000101,'0,1761000000000000100,1761000000000000101','市场部门',NULL,2,NULL,'15888888888','xxx@qq.com','0','0',1761000000000000103,1761100000000000001,'2026-08-08 14:44:42',NULL,NULL),(1761000000000000105,1761000000000000101,'0,1761000000000000100,1761000000000000101','测试部门',NULL,3,NULL,'15888888888','xxx@qq.com','0','0',1761000000000000103,1761100000000000001,'2026-08-08 14:44:42',NULL,NULL),(1761000000000000106,1761000000000000101,'0,1761000000000000100,1761000000000000101','财务部门',NULL,4,NULL,'15888888888','xxx@qq.com','0','0',1761000000000000103,1761100000000000001,'2026-08-08 14:44:42',NULL,NULL),(1761000000000000107,1761000000000000101,'0,1761000000000000100,1761000000000000101','运维部门',NULL,5,NULL,'15888888888','xxx@qq.com','0','0',1761000000000000103,1761100000000000001,'2026-08-08 14:44:42',NULL,NULL),(1761000000000000108,1761000000000000102,'0,1761000000000000100,1761000000000000102','市场部门',NULL,1,NULL,'15888888888','xxx@qq.com','0','0',1761000000000000103,1761100000000000001,'2026-08-08 14:44:42',NULL,NULL),(1761000000000000109,1761000000000000102,'0,1761000000000000100,1761000000000000102','财务部门',NULL,2,NULL,'15888888888','xxx@qq.com','0','0',1761000000000000103,1761100000000000001,'2026-08-08 14:44:42',NULL,NULL);
/*!40000 ALTER TABLE `sys_dept` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dict_data`
--

DROP TABLE IF EXISTS `sys_dict_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_data` (
  `dict_code` bigint NOT NULL COMMENT '字典编码',
  `dict_sort` int DEFAULT '0' COMMENT '字典排序',
  `dict_label` varchar(100) DEFAULT '' COMMENT '字典标签',
  `dict_value` varchar(100) DEFAULT '' COMMENT '字典键值',
  `dict_type` varchar(100) DEFAULT '' COMMENT '字典类型',
  `css_class` varchar(100) DEFAULT NULL COMMENT '样式属性（其他样式扩展）',
  `list_class` varchar(100) DEFAULT NULL COMMENT '表格回显样式',
  `is_default` char(1) DEFAULT 'N' COMMENT '是否默认（Y是 N否）',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`dict_code`),
  KEY `idx_sys_dict_data_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典数据表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dict_data`
--

LOCK TABLES `sys_dict_data` WRITE;
/*!40000 ALTER TABLE `sys_dict_data` DISABLE KEYS */;
INSERT INTO `sys_dict_data` VALUES (1761600000000000001,1,'男','0','sys_user_gender','','','Y',1761000000000000103,1761100000000000001,'2026-08-08 14:45:00',NULL,NULL,'性别男'),(1761600000000000002,2,'女','1','sys_user_gender','','','N',1761000000000000103,1761100000000000001,'2026-08-08 14:45:00',NULL,NULL,'性别女'),(1761600000000000003,3,'未知','2','sys_user_gender','','','N',1761000000000000103,1761100000000000001,'2026-08-08 14:45:00',NULL,NULL,'性别未知'),(1761600000000000004,1,'显示','0','sys_show_hide','','primary','Y',1761000000000000103,1761100000000000001,'2026-08-08 14:45:00',NULL,NULL,'显示菜单'),(1761600000000000005,2,'隐藏','1','sys_show_hide','','danger','N',1761000000000000103,1761100000000000001,'2026-08-08 14:45:00',NULL,NULL,'隐藏菜单'),(1761600000000000006,1,'正常','0','sys_normal_disable','','primary','Y',1761000000000000103,1761100000000000001,'2026-08-08 14:45:00',NULL,NULL,'正常状态'),(1761600000000000007,2,'停用','1','sys_normal_disable','','danger','N',1761000000000000103,1761100000000000001,'2026-08-08 14:45:00',NULL,NULL,'停用状态'),(1761600000000000012,1,'是','Y','sys_yes_no','','primary','Y',1761000000000000103,1761100000000000001,'2026-08-08 14:45:00',NULL,NULL,'系统默认是'),(1761600000000000013,2,'否','N','sys_yes_no','','danger','N',1761000000000000103,1761100000000000001,'2026-08-08 14:45:01',NULL,NULL,'系统默认否'),(1761600000000000014,1,'通知','1','sys_notice_type','','warning','Y',1761000000000000103,1761100000000000001,'2026-08-08 14:45:01',NULL,NULL,'通知'),(1761600000000000015,2,'公告','2','sys_notice_type','','success','N',1761000000000000103,1761100000000000001,'2026-08-08 14:45:01',NULL,NULL,'公告'),(1761600000000000016,1,'正常','0','sys_notice_status','','primary','Y',1761000000000000103,1761100000000000001,'2026-08-08 14:45:01',NULL,NULL,'正常状态'),(1761600000000000017,2,'关闭','1','sys_notice_status','','danger','N',1761000000000000103,1761100000000000001,'2026-08-08 14:45:01',NULL,NULL,'关闭状态'),(1761600000000000018,1,'新增','1','sys_oper_type','','info','N',1761000000000000103,1761100000000000001,'2026-08-08 14:45:01',NULL,NULL,'新增操作'),(1761600000000000019,2,'修改','2','sys_oper_type','','info','N',1761000000000000103,1761100000000000001,'2026-08-08 14:45:01',NULL,NULL,'修改操作'),(1761600000000000020,3,'删除','3','sys_oper_type','','danger','N',1761000000000000103,1761100000000000001,'2026-08-08 14:45:01',NULL,NULL,'删除操作'),(1761600000000000021,4,'授权','4','sys_oper_type','','primary','N',1761000000000000103,1761100000000000001,'2026-08-08 14:45:01',NULL,NULL,'授权操作'),(1761600000000000022,5,'导出','5','sys_oper_type','','warning','N',1761000000000000103,1761100000000000001,'2026-08-08 14:45:01',NULL,NULL,'导出操作'),(1761600000000000023,6,'导入','6','sys_oper_type','','warning','N',1761000000000000103,1761100000000000001,'2026-08-08 14:45:01',NULL,NULL,'导入操作'),(1761600000000000024,7,'强退','7','sys_oper_type','','danger','N',1761000000000000103,1761100000000000001,'2026-08-08 14:45:01',NULL,NULL,'强退操作'),(1761600000000000025,8,'生成代码','8','sys_oper_type','','warning','N',1761000000000000103,1761100000000000001,'2026-08-08 14:45:01',NULL,NULL,'生成操作'),(1761600000000000026,9,'清空数据','9','sys_oper_type','','danger','N',1761000000000000103,1761100000000000001,'2026-08-08 14:45:01',NULL,NULL,'清空操作'),(1761600000000000027,1,'成功','0','sys_common_status','','primary','N',1761000000000000103,1761100000000000001,'2026-08-08 14:45:01',NULL,NULL,'正常状态'),(1761600000000000028,2,'失败','1','sys_common_status','','danger','N',1761000000000000103,1761100000000000001,'2026-08-08 14:45:01',NULL,NULL,'停用状态'),(1761600000000000029,99,'其他','0','sys_oper_type','','info','N',1761000000000000103,1761100000000000001,'2026-08-08 14:45:01',NULL,NULL,'其他操作'),(1761600000000000030,0,'密码认证','password','sys_grant_type','el-check-tag','default','N',1761000000000000103,1761100000000000001,'2026-08-08 14:45:01',NULL,NULL,'密码认证'),(1761600000000000031,0,'短信认证','sms','sys_grant_type','el-check-tag','default','N',1761000000000000103,1761100000000000001,'2026-08-08 14:45:02',NULL,NULL,'短信认证'),(1761600000000000032,0,'邮件认证','email','sys_grant_type','el-check-tag','default','N',1761000000000000103,1761100000000000001,'2026-08-08 14:45:02',NULL,NULL,'邮件认证'),(1761600000000000033,0,'小程序认证','xcx','sys_grant_type','el-check-tag','default','N',1761000000000000103,1761100000000000001,'2026-08-08 14:45:02',NULL,NULL,'小程序认证'),(1761600000000000034,0,'三方登录认证','social','sys_grant_type','el-check-tag','default','N',1761000000000000103,1761100000000000001,'2026-08-08 14:45:02',NULL,NULL,'三方登录认证'),(1761600000000000035,0,'PC','pc','sys_device_type','','default','N',1761000000000000103,1761100000000000001,'2026-08-08 14:45:02',NULL,NULL,'PC'),(1761600000000000036,0,'安卓','android','sys_device_type','','default','N',1761000000000000103,1761100000000000001,'2026-08-08 14:45:02',NULL,NULL,'安卓'),(1761600000000000037,0,'iOS','ios','sys_device_type','','default','N',1761000000000000103,1761100000000000001,'2026-08-08 14:45:02',NULL,NULL,'iOS'),(1761600000000000038,0,'小程序','xcx','sys_device_type','','default','N',1761000000000000103,1761100000000000001,'2026-08-08 14:45:02',NULL,NULL,'小程序');
/*!40000 ALTER TABLE `sys_dict_data` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dict_type`
--

DROP TABLE IF EXISTS `sys_dict_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_type` (
  `dict_id` bigint NOT NULL COMMENT '字典主键',
  `dict_name` varchar(100) DEFAULT '' COMMENT '字典名称',
  `dict_type` varchar(100) DEFAULT '' COMMENT '字典类型',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`dict_id`),
  UNIQUE KEY `dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典类型表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dict_type`
--

LOCK TABLES `sys_dict_type` WRITE;
/*!40000 ALTER TABLE `sys_dict_type` DISABLE KEYS */;
INSERT INTO `sys_dict_type` VALUES (1761500000000000001,'用户性别','sys_user_gender',1761000000000000103,1761100000000000001,'2026-08-08 14:44:58',NULL,NULL,'用户性别列表'),(1761500000000000002,'菜单状态','sys_show_hide',1761000000000000103,1761100000000000001,'2026-08-08 14:44:58',NULL,NULL,'菜单状态列表'),(1761500000000000003,'系统开关','sys_normal_disable',1761000000000000103,1761100000000000001,'2026-08-08 14:44:59',NULL,NULL,'系统开关列表'),(1761500000000000006,'系统是否','sys_yes_no',1761000000000000103,1761100000000000001,'2026-08-08 14:44:59',NULL,NULL,'系统是否列表'),(1761500000000000007,'通知类型','sys_notice_type',1761000000000000103,1761100000000000001,'2026-08-08 14:44:59',NULL,NULL,'通知类型列表'),(1761500000000000008,'通知状态','sys_notice_status',1761000000000000103,1761100000000000001,'2026-08-08 14:44:59',NULL,NULL,'通知状态列表'),(1761500000000000009,'操作类型','sys_oper_type',1761000000000000103,1761100000000000001,'2026-08-08 14:44:59',NULL,NULL,'操作类型列表'),(1761500000000000010,'系统状态','sys_common_status',1761000000000000103,1761100000000000001,'2026-08-08 14:44:59',NULL,NULL,'登录状态列表'),(1761500000000000011,'授权类型','sys_grant_type',1761000000000000103,1761100000000000001,'2026-08-08 14:44:59',NULL,NULL,'认证授权类型'),(1761500000000000012,'设备类型','sys_device_type',1761000000000000103,1761100000000000001,'2026-08-08 14:44:59',NULL,NULL,'客户端设备类型');
/*!40000 ALTER TABLE `sys_dict_type` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_login_info`
--

DROP TABLE IF EXISTS `sys_login_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_login_info` (
  `info_id` bigint NOT NULL COMMENT '访问ID',
  `user_name` varchar(50) DEFAULT '' COMMENT '用户账号',
  `client_key` varchar(32) DEFAULT '' COMMENT '客户端',
  `device_type` varchar(32) DEFAULT '' COMMENT '设备类型',
  `ipaddr` varchar(128) DEFAULT '' COMMENT '登录IP地址',
  `login_location` varchar(255) DEFAULT '' COMMENT '登录地点',
  `browser` varchar(50) DEFAULT '' COMMENT '浏览器类型',
  `os` varchar(50) DEFAULT '' COMMENT '操作系统',
  `status` char(1) DEFAULT '0' COMMENT '登录状态（0正常 1异常）',
  `msg` varchar(255) DEFAULT '' COMMENT '提示消息',
  `login_time` datetime DEFAULT NULL COMMENT '访问时间',
  PRIMARY KEY (`info_id`),
  KEY `idx_sys_login_info_s` (`status`),
  KEY `idx_sys_login_info_lt` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统访问记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_login_info`
--

LOCK TABLES `sys_login_info` WRITE;
/*!40000 ALTER TABLE `sys_login_info` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_login_info` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_menu`
--

DROP TABLE IF EXISTS `sys_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_menu` (
  `menu_id` bigint NOT NULL COMMENT '菜单ID',
  `menu_name` varchar(50) NOT NULL COMMENT '菜单名称',
  `parent_id` bigint DEFAULT '0' COMMENT '父菜单ID',
  `order_num` int DEFAULT '0' COMMENT '显示顺序',
  `path` varchar(200) DEFAULT '' COMMENT '路由地址',
  `component` varchar(255) DEFAULT NULL COMMENT '组件路径',
  `query_param` varchar(255) DEFAULT NULL COMMENT '路由参数',
  `is_frame` char(1) DEFAULT 'N' COMMENT '是否为外链（Y是 N否）',
  `is_cache` char(1) DEFAULT 'Y' COMMENT '是否缓存（Y缓存 N不缓存）',
  `menu_type` char(1) DEFAULT '' COMMENT '菜单类型（M目录 C菜单 F按钮）',
  `visible` char(1) DEFAULT '0' COMMENT '显示状态（0显示 1隐藏）',
  `status` char(1) DEFAULT '0' COMMENT '菜单状态（0正常 1停用）',
  `perms` varchar(100) DEFAULT NULL COMMENT '权限标识',
  `icon` varchar(100) DEFAULT '#' COMMENT '菜单图标',
  `active_menu` varchar(255) DEFAULT '' COMMENT '激活菜单路径',
  `ext` varchar(2000) DEFAULT '' COMMENT '扩展字段',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT '' COMMENT '备注',
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='菜单权限表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_menu`
--

LOCK TABLES `sys_menu` WRITE;
/*!40000 ALTER TABLE `sys_menu` DISABLE KEYS */;
INSERT INTO `sys_menu` VALUES (1761400000000000001,'系统管理',0,1,'system',NULL,'','N','Y','M','0','0','','system','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:45',NULL,NULL,'系统管理目录'),(1761400000000000002,'系统监控',0,3,'monitor',NULL,'','N','Y','M','0','0','','monitor','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:45',NULL,NULL,'系统监控目录'),(1761400000000000003,'系统工具',0,4,'tool',NULL,'','N','Y','M','0','0','','tool','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:45',NULL,NULL,'系统工具目录'),(1761400000000000004,'PLUS官网',0,9,'https://gitee.com/dromara/RuoYi-Vue-Plus',NULL,'','Y','Y','M','0','0','','guide','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:45',NULL,NULL,'RuoYi-Vue-Plus官网地址'),(1761400000000000005,'测试菜单',0,5,'demo',NULL,'','N','Y','M','0','0','','star','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:45',NULL,NULL,'测试菜单'),(1761400000000000008,'AI会话',0,8,'aichat','ai/chat/index','','N','Y','C','0','0','','checkbox','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:45',NULL,NULL,'AI聊天菜单'),(1761400000000000100,'用户管理',1761400000000000001,1,'user','system/user/index','','N','Y','C','0','0','system:user:list','user','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:45',NULL,NULL,'用户管理菜单'),(1761400000000000101,'角色管理',1761400000000000001,2,'role','system/role/index','','N','Y','C','0','0','system:role:list','peoples','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:45',NULL,NULL,'角色管理菜单'),(1761400000000000102,'菜单管理',1761400000000000001,3,'menu','system/menu/index','','N','Y','C','0','0','system:menu:list','tree-table','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:45',NULL,NULL,'菜单管理菜单'),(1761400000000000103,'部门管理',1761400000000000001,4,'dept','system/dept/index','','N','Y','C','0','0','system:dept:list','tree','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:45',NULL,NULL,'部门管理菜单'),(1761400000000000104,'岗位管理',1761400000000000001,5,'post','system/post/index','','N','Y','C','0','0','system:post:list','post','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:45',NULL,NULL,'岗位管理菜单'),(1761400000000000105,'字典管理',1761400000000000001,6,'dict','system/dict/index','','N','Y','C','0','0','system:dict:list','dict','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:45',NULL,NULL,'字典管理菜单'),(1761400000000000106,'参数设置',1761400000000000001,7,'config','system/config/index','','N','Y','C','0','0','system:config:list','edit','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:45',NULL,NULL,'参数设置菜单'),(1761400000000000107,'通知公告',1761400000000000001,8,'notice','system/notice/index','','N','Y','C','0','0','system:notice:list','message','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:45',NULL,NULL,'通知公告菜单'),(1761400000000000108,'日志管理',1761400000000000001,9,'log','','','N','Y','M','0','0','','log','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:46',NULL,NULL,'日志管理菜单'),(1761400000000000109,'在线用户',1761400000000000002,1,'online','monitor/online/index','','N','Y','C','0','0','monitor:online:list','online','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:46',NULL,NULL,'在线用户菜单'),(1761400000000000113,'缓存监控',1761400000000000002,5,'cache','monitor/cache/index','','N','Y','C','0','0','monitor:cache:list','redis','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:46',NULL,NULL,'缓存监控菜单'),(1761400000000000115,'代码生成',1761400000000000003,2,'gen','tool/gen/index','','N','Y','C','0','0','tool:gen:list','code','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:46',NULL,NULL,'代码生成菜单'),(1761400000000000116,'修改生成配置',1761400000000000003,2,'gen-edit/index/:tableId','tool/gen/editTable','','N','N','C','1','0','tool:gen:edit','#','/tool/gen','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:46',NULL,NULL,''),(1761400000000000117,'Admin监控',1761400000000000002,5,'Admin','monitor/admin/index','','N','Y','C','0','0','monitor:admin:list','dashboard','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:46',NULL,NULL,'Admin监控菜单'),(1761400000000000118,'文件管理',1761400000000000001,10,'oss','system/oss/index','','N','Y','C','0','0','system:oss:list','upload','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:46',NULL,NULL,'文件管理菜单'),(1761400000000000120,'任务调度中心',1761400000000000002,6,'snailjob','monitor/snailjob/index','','N','Y','C','0','0','monitor:snailjob:list','job','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:46',NULL,NULL,'SnailJob控制台菜单'),(1761400000000000121,'AI控制台',1761400000000000002,7,'snailai','monitor/snailai/index','','N','Y','C','0','0','monitor:snailai:list','checkbox','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:46',NULL,NULL,'AI控制台菜单'),(1761400000000000123,'客户端管理',1761400000000000001,11,'client','system/client/index','','N','Y','C','0','0','system:client:list','international','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:46',NULL,NULL,'客户端管理菜单'),(1761400000000000130,'分配用户',1761400000000000001,2,'role-auth/user/:roleId','system/role/authUser','','N','N','C','1','0','system:role:edit','#','/system/role','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:46',NULL,NULL,''),(1761400000000000131,'分配角色',1761400000000000001,1,'user-auth/role/:userId','system/user/authRole','','N','N','C','1','0','system:user:edit','#','/system/user','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:46',NULL,NULL,''),(1761400000000000133,'文件配置管理',1761400000000000001,10,'oss-config/index','system/oss/config','','N','N','C','1','0','system:ossConfig:list','#','/system/oss','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:46',NULL,NULL,''),(1761400000000000500,'操作日志',1761400000000000108,1,'operlog','monitor/operlog/index','','N','Y','C','0','0','monitor:operlog:list','form','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:46',NULL,NULL,'操作日志菜单'),(1761400000000000501,'登录日志',1761400000000000108,2,'logininfo','monitor/logininfo/index','','N','Y','C','0','0','monitor:logininfo:list','logininfo','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:46',NULL,NULL,'登录日志菜单'),(1761400000000001001,'用户查询',1761400000000000100,1,'','','','N','Y','F','0','0','system:user:query','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:46',NULL,NULL,''),(1761400000000001002,'用户新增',1761400000000000100,2,'','','','N','Y','F','0','0','system:user:add','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:46',NULL,NULL,''),(1761400000000001003,'用户修改',1761400000000000100,3,'','','','N','Y','F','0','0','system:user:edit','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:46',NULL,NULL,''),(1761400000000001004,'用户删除',1761400000000000100,4,'','','','N','Y','F','0','0','system:user:remove','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:46',NULL,NULL,''),(1761400000000001005,'用户导出',1761400000000000100,5,'','','','N','Y','F','0','0','system:user:export','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:46',NULL,NULL,''),(1761400000000001006,'用户导入',1761400000000000100,6,'','','','N','Y','F','0','0','system:user:import','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001007,'重置密码',1761400000000000100,7,'','','','N','Y','F','0','0','system:user:resetPwd','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001008,'角色查询',1761400000000000101,1,'','','','N','Y','F','0','0','system:role:query','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001009,'角色新增',1761400000000000101,2,'','','','N','Y','F','0','0','system:role:add','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001010,'角色修改',1761400000000000101,3,'','','','N','Y','F','0','0','system:role:edit','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001011,'角色删除',1761400000000000101,4,'','','','N','Y','F','0','0','system:role:remove','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001012,'角色导出',1761400000000000101,5,'','','','N','Y','F','0','0','system:role:export','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001013,'菜单查询',1761400000000000102,1,'','','','N','Y','F','0','0','system:menu:query','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001014,'菜单新增',1761400000000000102,2,'','','','N','Y','F','0','0','system:menu:add','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001015,'菜单修改',1761400000000000102,3,'','','','N','Y','F','0','0','system:menu:edit','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001016,'菜单删除',1761400000000000102,4,'','','','N','Y','F','0','0','system:menu:remove','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001017,'部门查询',1761400000000000103,1,'','','','N','Y','F','0','0','system:dept:query','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001018,'部门新增',1761400000000000103,2,'','','','N','Y','F','0','0','system:dept:add','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001019,'部门修改',1761400000000000103,3,'','','','N','Y','F','0','0','system:dept:edit','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001020,'部门删除',1761400000000000103,4,'','','','N','Y','F','0','0','system:dept:remove','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001021,'岗位查询',1761400000000000104,1,'','','','N','Y','F','0','0','system:post:query','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001022,'岗位新增',1761400000000000104,2,'','','','N','Y','F','0','0','system:post:add','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001023,'岗位修改',1761400000000000104,3,'','','','N','Y','F','0','0','system:post:edit','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001024,'岗位删除',1761400000000000104,4,'','','','N','Y','F','0','0','system:post:remove','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001025,'岗位导出',1761400000000000104,5,'','','','N','Y','F','0','0','system:post:export','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001026,'字典查询',1761400000000000105,1,'#','','','N','Y','F','0','0','system:dict:query','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001027,'字典新增',1761400000000000105,2,'#','','','N','Y','F','0','0','system:dict:add','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001028,'字典修改',1761400000000000105,3,'#','','','N','Y','F','0','0','system:dict:edit','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001029,'字典删除',1761400000000000105,4,'#','','','N','Y','F','0','0','system:dict:remove','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001030,'字典导出',1761400000000000105,5,'#','','','N','Y','F','0','0','system:dict:export','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001031,'参数查询',1761400000000000106,1,'#','','','N','Y','F','0','0','system:config:query','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001032,'参数新增',1761400000000000106,2,'#','','','N','Y','F','0','0','system:config:add','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001033,'参数修改',1761400000000000106,3,'#','','','N','Y','F','0','0','system:config:edit','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001034,'参数删除',1761400000000000106,4,'#','','','N','Y','F','0','0','system:config:remove','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001035,'参数导出',1761400000000000106,5,'#','','','N','Y','F','0','0','system:config:export','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001036,'公告查询',1761400000000000107,1,'#','','','N','Y','F','0','0','system:notice:query','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:47',NULL,NULL,''),(1761400000000001037,'公告新增',1761400000000000107,2,'#','','','N','Y','F','0','0','system:notice:add','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:48',NULL,NULL,''),(1761400000000001038,'公告修改',1761400000000000107,3,'#','','','N','Y','F','0','0','system:notice:edit','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:48',NULL,NULL,''),(1761400000000001039,'公告删除',1761400000000000107,4,'#','','','N','Y','F','0','0','system:notice:remove','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:48',NULL,NULL,''),(1761400000000001040,'操作查询',1761400000000000500,1,'#','','','N','Y','F','0','0','monitor:operlog:query','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:48',NULL,NULL,''),(1761400000000001041,'操作删除',1761400000000000500,2,'#','','','N','Y','F','0','0','monitor:operlog:remove','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:48',NULL,NULL,''),(1761400000000001042,'日志导出',1761400000000000500,4,'#','','','N','Y','F','0','0','monitor:operlog:export','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:48',NULL,NULL,''),(1761400000000001043,'登录查询',1761400000000000501,1,'#','','','N','Y','F','0','0','monitor:logininfo:query','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:48',NULL,NULL,''),(1761400000000001044,'登录删除',1761400000000000501,2,'#','','','N','Y','F','0','0','monitor:logininfo:remove','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:48',NULL,NULL,''),(1761400000000001045,'日志导出',1761400000000000501,3,'#','','','N','Y','F','0','0','monitor:logininfo:export','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:48',NULL,NULL,''),(1761400000000001046,'在线查询',1761400000000000109,1,'#','','','N','Y','F','0','0','monitor:online:query','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:48',NULL,NULL,''),(1761400000000001047,'批量强退',1761400000000000109,2,'#','','','N','Y','F','0','0','monitor:online:batchLogout','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:48',NULL,NULL,''),(1761400000000001048,'单条强退',1761400000000000109,3,'#','','','N','Y','F','0','0','monitor:online:forceLogout','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:48',NULL,NULL,''),(1761400000000001050,'账户解锁',1761400000000000501,4,'#','','','N','Y','F','0','0','monitor:logininfo:unlock','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:48',NULL,NULL,''),(1761400000000001055,'生成查询',1761400000000000115,1,'#','','','N','Y','F','0','0','tool:gen:query','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:48',NULL,NULL,''),(1761400000000001056,'生成修改',1761400000000000115,2,'#','','','N','Y','F','0','0','tool:gen:edit','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:48',NULL,NULL,''),(1761400000000001057,'生成删除',1761400000000000115,3,'#','','','N','Y','F','0','0','tool:gen:remove','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:48',NULL,NULL,''),(1761400000000001058,'导入代码',1761400000000000115,2,'#','','','N','Y','F','0','0','tool:gen:import','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:48',NULL,NULL,''),(1761400000000001059,'预览代码',1761400000000000115,4,'#','','','N','Y','F','0','0','tool:gen:preview','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:48',NULL,NULL,''),(1761400000000001060,'生成代码',1761400000000000115,5,'#','','','N','Y','F','0','0','tool:gen:code','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:48',NULL,NULL,''),(1761400000000001061,'客户端管理查询',1761400000000000123,1,'#','','','N','Y','F','0','0','system:client:query','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:49',NULL,NULL,''),(1761400000000001062,'客户端管理新增',1761400000000000123,2,'#','','','N','Y','F','0','0','system:client:add','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:49',NULL,NULL,''),(1761400000000001063,'客户端管理修改',1761400000000000123,3,'#','','','N','Y','F','0','0','system:client:edit','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:49',NULL,NULL,''),(1761400000000001064,'客户端管理删除',1761400000000000123,4,'#','','','N','Y','F','0','0','system:client:remove','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:49',NULL,NULL,''),(1761400000000001065,'客户端管理导出',1761400000000000123,5,'#','','','N','Y','F','0','0','system:client:export','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:49',NULL,NULL,''),(1761400000000001500,'测试单表',1761400000000000005,1,'demo','demo/demo/index','','N','Y','C','0','0','demo:demo:list','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:49',NULL,NULL,'测试单表菜单'),(1761400000000001501,'测试单表查询',1761400000000001500,1,'#','','','N','Y','F','0','0','demo:demo:query','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:49',NULL,NULL,''),(1761400000000001502,'测试单表新增',1761400000000001500,2,'#','','','N','Y','F','0','0','demo:demo:add','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:49',NULL,NULL,''),(1761400000000001503,'测试单表修改',1761400000000001500,3,'#','','','N','Y','F','0','0','demo:demo:edit','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:49',NULL,NULL,''),(1761400000000001504,'测试单表删除',1761400000000001500,4,'#','','','N','Y','F','0','0','demo:demo:remove','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:49',NULL,NULL,''),(1761400000000001505,'测试单表导出',1761400000000001500,5,'#','','','N','Y','F','0','0','demo:demo:export','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:49',NULL,NULL,''),(1761400000000001506,'测试树表',1761400000000000005,1,'tree','demo/tree/index','','N','Y','C','0','0','demo:tree:list','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:49',NULL,NULL,'测试树表菜单'),(1761400000000001507,'测试树表查询',1761400000000001506,1,'#','','','N','Y','F','0','0','demo:tree:query','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:49',NULL,NULL,''),(1761400000000001508,'测试树表新增',1761400000000001506,2,'#','','','N','Y','F','0','0','demo:tree:add','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:49',NULL,NULL,''),(1761400000000001509,'测试树表修改',1761400000000001506,3,'#','','','N','Y','F','0','0','demo:tree:edit','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:49',NULL,NULL,''),(1761400000000001510,'测试树表删除',1761400000000001506,4,'#','','','N','Y','F','0','0','demo:tree:remove','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:49',NULL,NULL,''),(1761400000000001511,'测试树表导出',1761400000000001506,5,'#','','','N','Y','F','0','0','demo:tree:export','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:49',NULL,NULL,''),(1761400000000001600,'文件查询',1761400000000000118,1,'#','','','N','Y','F','0','0','system:oss:query','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:48',NULL,NULL,''),(1761400000000001601,'文件上传',1761400000000000118,2,'#','','','N','Y','F','0','0','system:oss:upload','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:48',NULL,NULL,''),(1761400000000001602,'文件下载',1761400000000000118,3,'#','','','N','Y','F','0','0','system:oss:download','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:49',NULL,NULL,''),(1761400000000001603,'文件删除',1761400000000000118,4,'#','','','N','Y','F','0','0','system:oss:remove','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:49',NULL,NULL,''),(1761400000000001620,'配置列表',1761400000000000118,5,'#','','','N','Y','F','0','0','system:ossConfig:list','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:49',NULL,NULL,''),(1761400000000001621,'配置添加',1761400000000000118,6,'#','','','N','Y','F','0','0','system:ossConfig:add','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:49',NULL,NULL,''),(1761400000000001622,'配置编辑',1761400000000000118,6,'#','','','N','Y','F','0','0','system:ossConfig:edit','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:49',NULL,NULL,''),(1761400000000001623,'配置删除',1761400000000000118,6,'#','','','N','Y','F','0','0','system:ossConfig:remove','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:44:49',NULL,NULL,''),(1761400000000020000,'创作端身份安全',0,10,'aivideo-identity',NULL,NULL,'N','Y','M','0','0','','safety-certificate','','',1761000000000000103,1761100000000000001,'2026-08-08 14:45:22',1761100000000000001,'2026-08-08 14:45:22','创作端身份安全目录'),(1761400000000020001,'用户',1761400000000020000,1,'app-user','aivideo/app-user/index',NULL,'N','Y','C','0','0','aivideo:app-user:query','user','','',1761000000000000103,1761100000000000001,'2026-08-08 14:45:22',1761100000000000001,'2026-08-08 14:45:22','创作端用户管理'),(1761400000000020002,'角色与权限',1761400000000020000,2,'app-role','aivideo/app-role/index',NULL,'N','Y','C','0','0','aivideo:app-role:query','peoples','','',1761000000000000103,1761100000000000001,'2026-08-08 14:45:22',1761100000000000001,'2026-08-08 14:45:22','创作端角色与权限管理'),(1761400000000020003,'认证客户端',1761400000000020000,3,'app-auth-client','aivideo/app-auth-client/index',NULL,'N','Y','C','0','0','aivideo:app-auth-client:query','international','','',1761000000000000103,1761100000000000001,'2026-08-08 14:45:22',1761100000000000001,'2026-08-08 14:45:22','创作端认证客户端管理'),(1761400000000020004,'创作端会话',1761400000000020000,4,'app-session','aivideo/app-session/index',NULL,'N','Y','C','0','0','aivideo:app-session:query','online','','',1761000000000000103,1761100000000000001,'2026-08-08 14:45:22',1761100000000000001,'2026-08-08 14:45:22','创作端会话管理'),(1761400000000020005,'创作端登录日志',1761400000000020000,5,'app-login-log','aivideo/app-login-log/index',NULL,'N','Y','C','0','0','aivideo:app-login-log:query','logininfo','','',1761000000000000103,1761100000000000001,'2026-08-08 14:45:22',1761100000000000001,'2026-08-08 14:45:22','创作端登录日志'),(1761400000000020006,'创作端安全审计',1761400000000020000,6,'app-security-audit','aivideo/app-security-audit/index',NULL,'N','Y','C','0','0','aivideo:app-security-audit:query','form','','',1761000000000000103,1761100000000000001,'2026-08-08 14:45:22',1761100000000000001,'2026-08-08 14:45:22','创作端安全审计'),(1761400000000020007,'用户新增',1761400000000020001,1,'#','',NULL,'N','Y','F','0','0','aivideo:app-user:add','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:45:22',1761100000000000001,'2026-08-08 14:45:22',''),(1761400000000020008,'用户修改',1761400000000020001,2,'#','',NULL,'N','Y','F','0','0','aivideo:app-user:edit','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:45:22',1761100000000000001,'2026-08-08 14:45:22',''),(1761400000000020009,'重置密码',1761400000000020001,3,'#','',NULL,'N','Y','F','0','0','aivideo:app-user:reset-password','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:45:22',1761100000000000001,'2026-08-08 14:45:22',''),(1761400000000020010,'强制下线',1761400000000020001,4,'#','',NULL,'N','Y','F','0','0','aivideo:app-user:kickout','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:45:22',1761100000000000001,'2026-08-08 14:45:22',''),(1761400000000020011,'分配角色',1761400000000020001,5,'#','',NULL,'N','Y','F','0','0','aivideo:app-user:assign-role','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:45:22',1761100000000000001,'2026-08-08 14:45:22',''),(1761400000000020012,'角色修改',1761400000000020002,1,'#','',NULL,'N','Y','F','0','0','aivideo:app-role:edit','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:45:22',1761100000000000001,'2026-08-08 14:45:22',''),(1761400000000020013,'分配权限',1761400000000020002,2,'#','',NULL,'N','Y','F','0','0','aivideo:app-role:assign-permission','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:45:22',1761100000000000001,'2026-08-08 14:45:22',''),(1761400000000020014,'客户端修改',1761400000000020003,1,'#','',NULL,'N','Y','F','0','0','aivideo:app-auth-client:edit','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:45:22',1761100000000000001,'2026-08-08 14:45:22',''),(1761400000000020015,'轮换密钥',1761400000000020003,2,'#','',NULL,'N','Y','F','0','0','aivideo:app-auth-client:rotate-secret','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:45:22',1761100000000000001,'2026-08-08 14:45:22',''),(1761400000000020016,'会话下线',1761400000000020004,1,'#','',NULL,'N','Y','F','0','0','aivideo:app-session:kickout','#','','',1761000000000000103,1761100000000000001,'2026-08-08 14:45:22',1761100000000000001,'2026-08-08 14:45:22','');
/*!40000 ALTER TABLE `sys_menu` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_message`
--

DROP TABLE IF EXISTS `sys_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_message` (
  `message_id` bigint NOT NULL COMMENT '消息ID',
  `category` varchar(20) NOT NULL COMMENT '消息分组(system/notice/workflow)',
  `type` varchar(20) NOT NULL COMMENT '消息类型',
  `source` varchar(20) NOT NULL COMMENT '消息来源',
  `title` varchar(100) DEFAULT '' COMMENT '标题',
  `message` varchar(500) DEFAULT '' COMMENT '摘要消息',
  `content` longtext COMMENT '详细内容',
  `data_json` longtext COMMENT '扩展数据JSON',
  `path` varchar(500) DEFAULT NULL COMMENT '前端跳转路径',
  `send_user_ids` varchar(2000) NOT NULL DEFAULT '0' COMMENT '目标用户ID串，0表示全局',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`message_id`),
  KEY `idx_sys_message_category_time` (`category`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_message`
--

LOCK TABLES `sys_message` WRITE;
/*!40000 ALTER TABLE `sys_message` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_message` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_notice`
--

DROP TABLE IF EXISTS `sys_notice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_notice` (
  `notice_id` bigint NOT NULL COMMENT '公告ID',
  `notice_title` varchar(50) NOT NULL COMMENT '公告标题',
  `notice_type` char(1) NOT NULL COMMENT '公告类型（1通知 2公告）',
  `notice_content` longblob COMMENT '公告内容',
  `status` char(1) DEFAULT '0' COMMENT '公告状态（0正常 1关闭）',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`notice_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知公告表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_notice`
--

LOCK TABLES `sys_notice` WRITE;
/*!40000 ALTER TABLE `sys_notice` DISABLE KEYS */;
INSERT INTO `sys_notice` VALUES (1761800000000000001,'温馨提醒：2018-07-01 新版本发布啦','2',0xE696B0E78988E69CACE58685E5AEB9,'0',1761000000000000103,1761100000000000001,'2026-08-08 14:45:05',NULL,NULL,'管理员'),(1761800000000000002,'维护通知：2018-07-01 系统凌晨维护','1',0xE7BBB4E68AA4E58685E5AEB9,'0',1761000000000000103,1761100000000000001,'2026-08-08 14:45:05',NULL,NULL,'管理员');
/*!40000 ALTER TABLE `sys_notice` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_oper_log`
--

DROP TABLE IF EXISTS `sys_oper_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_oper_log` (
  `oper_id` bigint NOT NULL COMMENT '日志主键',
  `title` varchar(50) DEFAULT '' COMMENT '模块标题',
  `business_type` int DEFAULT '0' COMMENT '业务类型（0其它 1新增 2修改 3删除）',
  `method` varchar(100) DEFAULT '' COMMENT '方法名称',
  `request_method` varchar(10) DEFAULT '' COMMENT '请求方式',
  `operator_type` int DEFAULT '0' COMMENT '操作类别（0其它 1后台用户 2手机端用户）',
  `oper_name` varchar(50) DEFAULT '' COMMENT '操作人员',
  `user_id` bigint DEFAULT NULL COMMENT '操作用户ID',
  `dept_id` bigint DEFAULT NULL COMMENT '操作部门ID',
  `dept_name` varchar(50) DEFAULT '' COMMENT '部门名称',
  `client_key` varchar(32) DEFAULT '' COMMENT '客户端',
  `device_type` varchar(32) DEFAULT '' COMMENT '设备类型',
  `browser` varchar(50) DEFAULT '' COMMENT '浏览器类型',
  `os` varchar(50) DEFAULT '' COMMENT '操作系统',
  `oper_url` varchar(255) DEFAULT '' COMMENT '请求URL',
  `oper_ip` varchar(128) DEFAULT '' COMMENT '主机地址',
  `oper_location` varchar(255) DEFAULT '' COMMENT '操作地点',
  `oper_param` varchar(4000) DEFAULT '' COMMENT '请求参数',
  `json_result` varchar(4000) DEFAULT '' COMMENT '返回参数',
  `status` int DEFAULT '0' COMMENT '操作状态（0正常 1异常）',
  `error_msg` varchar(4000) DEFAULT '' COMMENT '错误消息',
  `oper_time` datetime DEFAULT NULL COMMENT '操作时间',
  `cost_time` bigint DEFAULT '0' COMMENT '消耗时间',
  PRIMARY KEY (`oper_id`),
  KEY `idx_sys_oper_log_bt` (`business_type`),
  KEY `idx_sys_oper_log_uid` (`user_id`),
  KEY `idx_sys_oper_log_s` (`status`),
  KEY `idx_sys_oper_log_ot` (`oper_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_oper_log`
--

LOCK TABLES `sys_oper_log` WRITE;
/*!40000 ALTER TABLE `sys_oper_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_oper_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_oss`
--

DROP TABLE IF EXISTS `sys_oss`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_oss` (
  `oss_id` bigint NOT NULL COMMENT '对象存储主键',
  `file_name` varchar(255) NOT NULL DEFAULT '' COMMENT '文件名',
  `original_name` varchar(255) NOT NULL DEFAULT '' COMMENT '原名',
  `file_suffix` varchar(10) NOT NULL DEFAULT '' COMMENT '文件后缀名',
  `url` varchar(500) NOT NULL COMMENT 'URL地址',
  `ext1` text COMMENT '扩展字段',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `create_by` bigint DEFAULT NULL COMMENT '上传人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `service` varchar(20) NOT NULL DEFAULT 'minio' COMMENT '服务商',
  PRIMARY KEY (`oss_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='OSS对象存储表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_oss`
--

LOCK TABLES `sys_oss` WRITE;
/*!40000 ALTER TABLE `sys_oss` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_oss` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_oss_config`
--

DROP TABLE IF EXISTS `sys_oss_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_oss_config` (
  `oss_config_id` bigint NOT NULL COMMENT '主键',
  `config_key` varchar(20) NOT NULL DEFAULT '' COMMENT '配置key',
  `access_key` varchar(255) DEFAULT '' COMMENT 'accessKey',
  `secret_key` varchar(255) DEFAULT '' COMMENT '秘钥',
  `bucket_name` varchar(255) DEFAULT '' COMMENT '桶名称',
  `prefix` varchar(255) DEFAULT '' COMMENT '前缀',
  `endpoint` varchar(255) DEFAULT '' COMMENT '访问站点',
  `domain_url` varchar(255) DEFAULT '' COMMENT '自定义域名',
  `is_https` char(1) DEFAULT 'N' COMMENT '是否https（Y=是,N=否）',
  `region` varchar(255) DEFAULT '' COMMENT '域',
  `access_policy` char(1) NOT NULL DEFAULT '1' COMMENT '桶权限类型(0=private 1=public 2=custom)',
  `status` char(1) DEFAULT 'N' COMMENT '是否默认（Y=是,N=否）',
  `ext1` varchar(255) DEFAULT '' COMMENT '扩展字段',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`oss_config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='对象存储配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_oss_config`
--

LOCK TABLES `sys_oss_config` WRITE;
/*!40000 ALTER TABLE `sys_oss_config` DISABLE KEYS */;
INSERT INTO `sys_oss_config` VALUES (1761900000000000001,'minio','ruoyi','ruoyi123','ruoyi','','127.0.0.1:9000','','N','','1','Y','',1761000000000000103,1761100000000000001,'2026-08-08 14:45:09',1761100000000000001,'2026-08-08 14:45:09',NULL),(1761900000000000002,'qiniu','XXXXXXXXXXXXXXX','XXXXXXXXXXXXXXX','ruoyi','','s3-cn-north-1.qiniucs.com','','N','','1','N','',1761000000000000103,1761100000000000001,'2026-08-08 14:45:09',1761100000000000001,'2026-08-08 14:45:09',NULL),(1761900000000000003,'aliyun','XXXXXXXXXXXXXXX','XXXXXXXXXXXXXXX','ruoyi','','oss-cn-beijing.aliyuncs.com','','N','','1','N','',1761000000000000103,1761100000000000001,'2026-08-08 14:45:09',1761100000000000001,'2026-08-08 14:45:09',NULL),(1761900000000000004,'qcloud','XXXXXXXXXXXXXXX','XXXXXXXXXXXXXXX','ruoyi-1240000000','','cos.ap-beijing.myqcloud.com','','N','ap-beijing','1','N','',1761000000000000103,1761100000000000001,'2026-08-08 14:45:09',1761100000000000001,'2026-08-08 14:45:09',NULL),(1761900000000000005,'image','ruoyi','ruoyi123','ruoyi','image','127.0.0.1:9000','','N','','1','N','',1761000000000000103,1761100000000000001,'2026-08-08 14:45:09',1761100000000000001,'2026-08-08 14:45:09',NULL);
/*!40000 ALTER TABLE `sys_oss_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_post`
--

DROP TABLE IF EXISTS `sys_post`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_post` (
  `post_id` bigint NOT NULL COMMENT '岗位ID',
  `dept_id` bigint NOT NULL COMMENT '部门id',
  `post_code` varchar(64) NOT NULL COMMENT '岗位编码',
  `post_category` varchar(100) DEFAULT NULL COMMENT '岗位类别编码',
  `post_name` varchar(50) NOT NULL COMMENT '岗位名称',
  `post_sort` int NOT NULL COMMENT '显示顺序',
  `status` char(1) NOT NULL COMMENT '状态（0正常 1停用）',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0代表存在 1代表删除）',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`post_id`),
  KEY `idx_sys_post_dept_id` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='岗位信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_post`
--

LOCK TABLES `sys_post` WRITE;
/*!40000 ALTER TABLE `sys_post` DISABLE KEYS */;
INSERT INTO `sys_post` VALUES (1761200000000000001,1761000000000000103,'ceo',NULL,'董事长',1,'0','0',1761000000000000103,1761100000000000001,'2026-08-08 14:44:43',NULL,NULL,''),(1761200000000000002,1761000000000000100,'se',NULL,'项目经理',2,'0','0',1761000000000000103,1761100000000000001,'2026-08-08 14:44:44',NULL,NULL,''),(1761200000000000003,1761000000000000100,'hr',NULL,'人力资源',3,'0','0',1761000000000000103,1761100000000000001,'2026-08-08 14:44:44',NULL,NULL,''),(1761200000000000004,1761000000000000100,'user',NULL,'普通员工',4,'0','0',1761000000000000103,1761100000000000001,'2026-08-08 14:44:44',NULL,NULL,'');
/*!40000 ALTER TABLE `sys_post` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_role`
--

DROP TABLE IF EXISTS `sys_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `role_name` varchar(30) NOT NULL COMMENT '角色名称',
  `role_key` varchar(100) NOT NULL COMMENT '角色权限字符串',
  `role_sort` int NOT NULL COMMENT '显示顺序',
  `data_scope` char(1) DEFAULT '1' COMMENT '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限 5：仅本人数据权限 6：部门及以下或本人数据权限）',
  `menu_check_strictly` tinyint(1) DEFAULT '1' COMMENT '菜单树选择项是否关联显示',
  `dept_check_strictly` tinyint(1) DEFAULT '1' COMMENT '部门树选择项是否关联显示',
  `status` char(1) NOT NULL COMMENT '角色状态（0正常 1停用）',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0代表存在 1代表删除）',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`role_id`),
  KEY `idx_sys_role_create_dept` (`create_dept`),
  KEY `idx_sys_role_create_by` (`create_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role`
--

LOCK TABLES `sys_role` WRITE;
/*!40000 ALTER TABLE `sys_role` DISABLE KEYS */;
INSERT INTO `sys_role` VALUES (1761300000000000001,'超级管理员','superadmin',1,'1',1,1,'0','0',1761000000000000103,1761100000000000001,'2026-08-08 14:44:45',NULL,NULL,'超级管理员'),(1761300000000000003,'本部门及以下','test1',3,'4',1,1,'0','0',1761000000000000103,1761100000000000001,'2026-08-08 14:44:45',NULL,NULL,''),(1761300000000000004,'仅本人','test2',4,'5',1,1,'0','0',1761000000000000103,1761100000000000001,'2026-08-08 14:44:45',NULL,NULL,'');
/*!40000 ALTER TABLE `sys_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_role_dept`
--

DROP TABLE IF EXISTS `sys_role_dept`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_dept` (
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `dept_id` bigint NOT NULL COMMENT '部门ID',
  PRIMARY KEY (`role_id`,`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色和部门关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role_dept`
--

LOCK TABLES `sys_role_dept` WRITE;
/*!40000 ALTER TABLE `sys_role_dept` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_role_dept` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_role_menu`
--

DROP TABLE IF EXISTS `sys_role_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_menu` (
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `menu_id` bigint NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (`role_id`,`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色和菜单关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role_menu`
--

LOCK TABLES `sys_role_menu` WRITE;
/*!40000 ALTER TABLE `sys_role_menu` DISABLE KEYS */;
INSERT INTO `sys_role_menu` VALUES (1761300000000000003,1761400000000000001),(1761300000000000003,1761400000000000005),(1761300000000000003,1761400000000000100),(1761300000000000003,1761400000000000101),(1761300000000000003,1761400000000000102),(1761300000000000003,1761400000000000103),(1761300000000000003,1761400000000000104),(1761300000000000003,1761400000000000105),(1761300000000000003,1761400000000000106),(1761300000000000003,1761400000000000107),(1761300000000000003,1761400000000000108),(1761300000000000003,1761400000000000118),(1761300000000000003,1761400000000000123),(1761300000000000003,1761400000000000130),(1761300000000000003,1761400000000000131),(1761300000000000003,1761400000000000133),(1761300000000000003,1761400000000000500),(1761300000000000003,1761400000000000501),(1761300000000000003,1761400000000001001),(1761300000000000003,1761400000000001002),(1761300000000000003,1761400000000001003),(1761300000000000003,1761400000000001004),(1761300000000000003,1761400000000001005),(1761300000000000003,1761400000000001006),(1761300000000000003,1761400000000001007),(1761300000000000003,1761400000000001008),(1761300000000000003,1761400000000001009),(1761300000000000003,1761400000000001010),(1761300000000000003,1761400000000001011),(1761300000000000003,1761400000000001012),(1761300000000000003,1761400000000001013),(1761300000000000003,1761400000000001014),(1761300000000000003,1761400000000001015),(1761300000000000003,1761400000000001016),(1761300000000000003,1761400000000001017),(1761300000000000003,1761400000000001018),(1761300000000000003,1761400000000001019),(1761300000000000003,1761400000000001020),(1761300000000000003,1761400000000001021),(1761300000000000003,1761400000000001022),(1761300000000000003,1761400000000001023),(1761300000000000003,1761400000000001024),(1761300000000000003,1761400000000001025),(1761300000000000003,1761400000000001026),(1761300000000000003,1761400000000001027),(1761300000000000003,1761400000000001028),(1761300000000000003,1761400000000001029),(1761300000000000003,1761400000000001030),(1761300000000000003,1761400000000001031),(1761300000000000003,1761400000000001032),(1761300000000000003,1761400000000001033),(1761300000000000003,1761400000000001034),(1761300000000000003,1761400000000001035),(1761300000000000003,1761400000000001036),(1761300000000000003,1761400000000001037),(1761300000000000003,1761400000000001038),(1761300000000000003,1761400000000001039),(1761300000000000003,1761400000000001040),(1761300000000000003,1761400000000001041),(1761300000000000003,1761400000000001042),(1761300000000000003,1761400000000001043),(1761300000000000003,1761400000000001044),(1761300000000000003,1761400000000001045),(1761300000000000003,1761400000000001050),(1761300000000000003,1761400000000001061),(1761300000000000003,1761400000000001062),(1761300000000000003,1761400000000001063),(1761300000000000003,1761400000000001064),(1761300000000000003,1761400000000001065),(1761300000000000003,1761400000000001500),(1761300000000000003,1761400000000001501),(1761300000000000003,1761400000000001502),(1761300000000000003,1761400000000001503),(1761300000000000003,1761400000000001504),(1761300000000000003,1761400000000001505),(1761300000000000003,1761400000000001506),(1761300000000000003,1761400000000001507),(1761300000000000003,1761400000000001508),(1761300000000000003,1761400000000001509),(1761300000000000003,1761400000000001510),(1761300000000000003,1761400000000001511),(1761300000000000003,1761400000000001600),(1761300000000000003,1761400000000001601),(1761300000000000003,1761400000000001602),(1761300000000000003,1761400000000001603),(1761300000000000003,1761400000000001620),(1761300000000000003,1761400000000001621),(1761300000000000003,1761400000000001622),(1761300000000000003,1761400000000001623),(1761300000000000003,1761400000000011616),(1761300000000000003,1761400000000011618),(1761300000000000003,1761400000000011619),(1761300000000000003,1761400000000011622),(1761300000000000003,1761400000000011623),(1761300000000000003,1761400000000011629),(1761300000000000003,1761400000000011632),(1761300000000000003,1761400000000011633),(1761300000000000003,1761400000000011638),(1761300000000000003,1761400000000011639),(1761300000000000003,1761400000000011640),(1761300000000000003,1761400000000011641),(1761300000000000003,1761400000000011642),(1761300000000000003,1761400000000011643),(1761300000000000003,1761400000000011701),(1761300000000000004,1761400000000000005),(1761300000000000004,1761400000000001500),(1761300000000000004,1761400000000001501),(1761300000000000004,1761400000000001502),(1761300000000000004,1761400000000001503),(1761300000000000004,1761400000000001504),(1761300000000000004,1761400000000001505),(1761300000000000004,1761400000000001506),(1761300000000000004,1761400000000001507),(1761300000000000004,1761400000000001508),(1761300000000000004,1761400000000001509),(1761300000000000004,1761400000000001510),(1761300000000000004,1761400000000001511);
/*!40000 ALTER TABLE `sys_role_menu` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_social`
--

DROP TABLE IF EXISTS `sys_social`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_social` (
  `id` bigint NOT NULL COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `auth_id` varchar(255) NOT NULL COMMENT '平台+平台唯一id',
  `source` varchar(255) NOT NULL COMMENT '用户来源',
  `open_id` varchar(255) DEFAULT NULL COMMENT '平台编号唯一id',
  `user_name` varchar(30) NOT NULL COMMENT '登录账号',
  `nick_name` varchar(30) DEFAULT '' COMMENT '用户昵称',
  `email` varchar(255) DEFAULT '' COMMENT '用户邮箱',
  `avatar` varchar(500) DEFAULT '' COMMENT '头像地址',
  `access_token` varchar(2000) NOT NULL COMMENT '用户的授权令牌',
  `expire_in` int DEFAULT NULL COMMENT '用户的授权令牌的有效期，部分平台可能没有',
  `refresh_token` varchar(2000) DEFAULT NULL COMMENT '刷新令牌，部分平台可能没有',
  `access_code` varchar(255) DEFAULT NULL COMMENT '平台的授权信息，部分平台可能没有',
  `union_id` varchar(255) DEFAULT NULL COMMENT '用户的 unionid',
  `scope` varchar(255) DEFAULT NULL COMMENT '授予的权限，部分平台可能没有',
  `token_type` varchar(255) DEFAULT NULL COMMENT '个别平台的授权信息，部分平台可能没有',
  `id_token` varchar(2000) DEFAULT NULL COMMENT 'id token，部分平台可能没有',
  `mac_algorithm` varchar(255) DEFAULT NULL COMMENT '小米平台用户的附带属性，部分平台可能没有',
  `mac_key` varchar(255) DEFAULT NULL COMMENT '小米平台用户的附带属性，部分平台可能没有',
  `code` varchar(255) DEFAULT NULL COMMENT '用户的授权code，部分平台可能没有',
  `oauth_token` varchar(255) DEFAULT NULL COMMENT 'Twitter平台用户的附带属性，部分平台可能没有',
  `oauth_token_secret` varchar(255) DEFAULT NULL COMMENT 'Twitter平台用户的附带属性，部分平台可能没有',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0代表存在 1代表删除）',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='社会化关系表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_social`
--

LOCK TABLES `sys_social` WRITE;
/*!40000 ALTER TABLE `sys_social` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_social` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `dept_id` bigint DEFAULT NULL COMMENT '部门ID',
  `user_name` varchar(30) NOT NULL COMMENT '用户账号',
  `nick_name` varchar(30) NOT NULL COMMENT '用户昵称',
  `user_type` varchar(10) DEFAULT 'sys_user' COMMENT '用户类型（sys_user系统用户）',
  `email` varchar(50) DEFAULT '' COMMENT '用户邮箱',
  `phone_number` varchar(11) DEFAULT '' COMMENT '手机号码',
  `gender` char(1) DEFAULT '0' COMMENT '用户性别（0男 1女 2未知）',
  `avatar` bigint DEFAULT NULL COMMENT '头像地址',
  `password` varchar(100) DEFAULT '' COMMENT '密码',
  `status` char(1) DEFAULT '0' COMMENT '账号状态（0正常 1停用）',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0代表存在 1代表删除）',
  `login_ip` varchar(128) DEFAULT '' COMMENT '最后登录IP',
  `login_date` datetime DEFAULT NULL COMMENT '最后登录时间',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_by` bigint DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`user_id`),
  KEY `idx_sys_user_dept_id` (`dept_id`),
  KEY `idx_sys_user_create_by` (`create_by`),
  KEY `idx_sys_user_user_name` (`user_name`),
  KEY `idx_sys_user_phone` (`phone_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user`
--

LOCK TABLES `sys_user` WRITE;
/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
INSERT INTO `sys_user` VALUES (1761100000000000001,1761000000000000103,'admin','疯狂的狮子Li','sys_user','crazyLionLi@163.com','15888888888','1',NULL,'$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','0','0','127.0.0.1','2026-08-08 14:44:43',1761000000000000103,1761100000000000001,'2026-08-08 14:44:43',NULL,NULL,'管理员'),(1761100000000000003,1761000000000000108,'test','本部门及以下 密码666666','sys_user','','','0',NULL,'$2a$10$b8yUzN0C71sbz.PhNOCgJe.Tu1yWC3RNrTyjSQ8p1W0.aaUXUJ.Ne','0','0','127.0.0.1','2026-08-08 14:44:43',1761000000000000103,1761100000000000001,'2026-08-08 14:44:43',1761100000000000003,'2026-08-08 14:44:43',NULL),(1761100000000000004,1761000000000000102,'test1','仅本人 密码666666','sys_user','','','0',NULL,'$2a$10$b8yUzN0C71sbz.PhNOCgJe.Tu1yWC3RNrTyjSQ8p1W0.aaUXUJ.Ne','0','0','127.0.0.1','2026-08-08 14:44:43',1761000000000000103,1761100000000000001,'2026-08-08 14:44:43',1761100000000000004,'2026-08-08 14:44:43',NULL);
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user_post`
--

DROP TABLE IF EXISTS `sys_user_post`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_post` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `post_id` bigint NOT NULL COMMENT '岗位ID',
  PRIMARY KEY (`user_id`,`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户与岗位关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user_post`
--

LOCK TABLES `sys_user_post` WRITE;
/*!40000 ALTER TABLE `sys_user_post` DISABLE KEYS */;
INSERT INTO `sys_user_post` VALUES (1761100000000000001,1761200000000000001);
/*!40000 ALTER TABLE `sys_user_post` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user_role`
--

DROP TABLE IF EXISTS `sys_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`user_id`,`role_id`),
  KEY `idx_sys_user_role_rid` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户和角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user_role`
--

LOCK TABLES `sys_user_role` WRITE;
/*!40000 ALTER TABLE `sys_user_role` DISABLE KEYS */;
INSERT INTO `sys_user_role` VALUES (1761100000000000001,1761300000000000001),(1761100000000000003,1761300000000000003),(1761100000000000004,1761300000000000004);
/*!40000 ALTER TABLE `sys_user_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `test_demo`
--

DROP TABLE IF EXISTS `test_demo`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_demo` (
  `id` bigint NOT NULL COMMENT '主键',
  `dept_id` bigint DEFAULT NULL COMMENT '部门id',
  `user_id` bigint DEFAULT NULL COMMENT '用户id',
  `order_num` int DEFAULT '0' COMMENT '排序号',
  `test_key` varchar(255) DEFAULT NULL COMMENT 'key键',
  `value` varchar(255) DEFAULT NULL COMMENT '值',
  `version` int DEFAULT '0' COMMENT '版本',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `del_flag` int DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='测试单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `test_demo`
--

LOCK TABLES `test_demo` WRITE;
/*!40000 ALTER TABLE `test_demo` DISABLE KEYS */;
INSERT INTO `test_demo` VALUES (1762100000000000001,1761000000000000102,1761100000000000004,1,'测试数据权限','测试',0,1761000000000000103,'2026-08-08 14:45:11',1761100000000000001,NULL,NULL,0),(1762100000000000002,1761000000000000102,1761100000000000003,2,'子节点1','111',0,1761000000000000103,'2026-08-08 14:45:11',1761100000000000001,NULL,NULL,0),(1762100000000000003,1761000000000000102,1761100000000000003,3,'子节点2','222',0,1761000000000000103,'2026-08-08 14:45:11',1761100000000000001,NULL,NULL,0),(1762100000000000004,1761000000000000108,1761100000000000004,4,'测试数据','demo',0,1761000000000000103,'2026-08-08 14:45:11',1761100000000000001,NULL,NULL,0),(1762100000000000005,1761000000000000108,1761100000000000003,13,'子节点11','1111',0,1761000000000000103,'2026-08-08 14:45:11',1761100000000000001,NULL,NULL,0),(1762100000000000006,1761000000000000108,1761100000000000003,12,'子节点22','2222',0,1761000000000000103,'2026-08-08 14:45:11',1761100000000000001,NULL,NULL,0),(1762100000000000007,1761000000000000108,1761100000000000003,11,'子节点33','3333',0,1761000000000000103,'2026-08-08 14:45:11',1761100000000000001,NULL,NULL,0),(1762100000000000008,1761000000000000108,1761100000000000003,10,'子节点44','4444',0,1761000000000000103,'2026-08-08 14:45:11',1761100000000000001,NULL,NULL,0),(1762100000000000009,1761000000000000108,1761100000000000003,9,'子节点55','5555',0,1761000000000000103,'2026-08-08 14:45:12',1761100000000000001,NULL,NULL,0),(1762100000000000010,1761000000000000108,1761100000000000003,8,'子节点66','6666',0,1761000000000000103,'2026-08-08 14:45:12',1761100000000000001,NULL,NULL,0),(1762100000000000011,1761000000000000108,1761100000000000003,7,'子节点77','7777',0,1761000000000000103,'2026-08-08 14:45:12',1761100000000000001,NULL,NULL,0),(1762100000000000012,1761000000000000108,1761100000000000003,6,'子节点88','8888',0,1761000000000000103,'2026-08-08 14:45:12',1761100000000000001,NULL,NULL,0),(1762100000000000013,1761000000000000108,1761100000000000003,5,'子节点99','9999',0,1761000000000000103,'2026-08-08 14:45:12',1761100000000000001,NULL,NULL,0);
/*!40000 ALTER TABLE `test_demo` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `test_tree`
--

DROP TABLE IF EXISTS `test_tree`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_tree` (
  `id` bigint NOT NULL COMMENT '主键',
  `parent_id` bigint DEFAULT '0' COMMENT '父id',
  `dept_id` bigint DEFAULT NULL COMMENT '部门id',
  `user_id` bigint DEFAULT NULL COMMENT '用户id',
  `tree_name` varchar(255) DEFAULT NULL COMMENT '值',
  `version` int DEFAULT '0' COMMENT '版本',
  `create_dept` bigint DEFAULT NULL COMMENT '创建部门',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `del_flag` int DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='测试树表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `test_tree`
--

LOCK TABLES `test_tree` WRITE;
/*!40000 ALTER TABLE `test_tree` DISABLE KEYS */;
INSERT INTO `test_tree` VALUES (1762200000000000001,0,1761000000000000102,1761100000000000004,'测试数据权限',0,1761000000000000103,'2026-08-08 14:45:12',1761100000000000001,NULL,NULL,0),(1762200000000000002,1762200000000000001,1761000000000000102,1761100000000000003,'子节点1',0,1761000000000000103,'2026-08-08 14:45:12',1761100000000000001,NULL,NULL,0),(1762200000000000003,1762200000000000002,1761000000000000102,1761100000000000003,'子节点2',0,1761000000000000103,'2026-08-08 14:45:12',1761100000000000001,NULL,NULL,0),(1762200000000000004,0,1761000000000000108,1761100000000000004,'测试树1',0,1761000000000000103,'2026-08-08 14:45:12',1761100000000000001,NULL,NULL,0),(1762200000000000005,1762200000000000004,1761000000000000108,1761100000000000003,'子节点11',0,1761000000000000103,'2026-08-08 14:45:12',1761100000000000001,NULL,NULL,0),(1762200000000000006,1762200000000000004,1761000000000000108,1761100000000000003,'子节点22',0,1761000000000000103,'2026-08-08 14:45:13',1761100000000000001,NULL,NULL,0),(1762200000000000007,1762200000000000004,1761000000000000108,1761100000000000003,'子节点33',0,1761000000000000103,'2026-08-08 14:45:13',1761100000000000001,NULL,NULL,0),(1762200000000000008,1762200000000000005,1761000000000000108,1761100000000000003,'子节点44',0,1761000000000000103,'2026-08-08 14:45:13',1761100000000000001,NULL,NULL,0),(1762200000000000009,1762200000000000006,1761000000000000108,1761100000000000003,'子节点55',0,1761000000000000103,'2026-08-08 14:45:13',1761100000000000001,NULL,NULL,0),(1762200000000000010,1762200000000000007,1761000000000000108,1761100000000000003,'子节点66',0,1761000000000000103,'2026-08-08 14:45:13',1761100000000000001,NULL,NULL,0),(1762200000000000011,1762200000000000007,1761000000000000108,1761100000000000003,'子节点77',0,1761000000000000103,'2026-08-08 14:45:13',1761100000000000001,NULL,NULL,0),(1762200000000000012,1762200000000000010,1761000000000000108,1761100000000000003,'子节点88',0,1761000000000000103,'2026-08-08 14:45:13',1761100000000000001,NULL,NULL,0),(1762200000000000013,1762200000000000010,1761000000000000108,1761100000000000003,'子节点99',0,1761000000000000103,'2026-08-08 14:45:13',1761100000000000001,NULL,NULL,0);
/*!40000 ALTER TABLE `test_tree` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping events for database 'ai_video_test'
--

--
-- Dumping routines for database 'ai_video_test'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-08-09  7:05:21
