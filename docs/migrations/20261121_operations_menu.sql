-- Run after schema deployment. No automatic grants to ordinary roles.
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT '业务工作台',0,84,'/operations','Layout',1,1,'M','0','0','','list','admin',NOW()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE path='/operations');
SET @ef_parent=(SELECT menu_id FROM sys_menu WHERE path='/operations' ORDER BY menu_id LIMIT 1);
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT '库存采购与审批',@ef_parent,1,'overview','operations/index',1,1,'C','0','0','erp:operations:read','list','admin',NOW()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='erp:operations:read');
SET @ef_page=(SELECT menu_id FROM sys_menu WHERE perms='erp:operations:read' ORDER BY menu_id LIMIT 1);
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time) SELECT '财务审批',@ef_page,1,'','F','0','0','erp:operations:finance','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='erp:operations:finance');

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time) SELECT '记账与收货',@ef_page,1,'','F','0','0','erp:operations:post','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='erp:operations:post');

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time) SELECT '制单与补货',@ef_page,1,'','F','0','0','erp:operations:write','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='erp:operations:write');

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time) SELECT '业务审批',@ef_page,1,'','F','0','0','erp:operations:approve','admin',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='erp:operations:approve');

