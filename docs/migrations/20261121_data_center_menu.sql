-- Idempotent menu installation. Do not grant global data permissions to existing non-admin roles automatically.
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT '数据中台',0,85,'/data-center','Layout',1,1,'M','0','0','','chart','admin',NOW()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE path='/data-center');
SET @dc_parent=(SELECT menu_id FROM sys_menu WHERE path='/data-center' ORDER BY menu_id LIMIT 1);
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT '数据工作台',@dc_parent,1,'overview','data-center/index',1,1,'C','0','0','data:center:read','chart','admin',NOW()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='data:center:read');
SET @dc_page=(SELECT menu_id FROM sys_menu WHERE perms='data:center:read' ORDER BY menu_id LIMIT 1);
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,visible,status,perms,create_by,create_time)
SELECT '全域数据同步与映射管理',@dc_page,1,'','F','0','0','data:center:manage','admin',NOW()
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='data:center:manage');
