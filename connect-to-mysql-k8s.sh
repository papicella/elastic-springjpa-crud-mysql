export MYSQL_HOST=$(kubectl get svc pas-mysql-public -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
export MYSQL_ROOT_PASSWORD=$(kubectl get secret pas-mysql -o jsonpath="{.data.mysql-root-password}" | base64 --decode; echo)

echo ""
echo "mysql -h $MYSQL_HOST -P3306 -u root -p$MYSQL_ROOT_PASSWORD"
echo ""

mysql -h $MYSQL_HOST -P3306 -u root -p$MYSQL_ROOT_PASSWORD