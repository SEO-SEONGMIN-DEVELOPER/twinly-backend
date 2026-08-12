#!/bin/bash
set -eux
export DEBIAN_FRONTEND=noninteractive

install -d -o ubuntu -g ubuntu -m 0700 /home/ubuntu/.ssh
echo 'ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIPAIth2m1CmXf555ZKYXBdzq7zwtU20PRDdcMnt10lr8 twinly_stage_api' >> /home/ubuntu/.ssh/authorized_keys
chown ubuntu:ubuntu /home/ubuntu/.ssh/authorized_keys
chmod 0600 /home/ubuntu/.ssh/authorized_keys

cat > /etc/security/limits.d/99-loadgen.conf <<INNER
*    soft nofile 1048576
*    hard nofile 1048576
root soft nofile 1048576
root hard nofile 1048576
INNER

cat > /etc/sysctl.d/99-loadgen.conf <<INNER
net.ipv4.ip_local_port_range = 10000 65535
net.ipv4.tcp_tw_reuse = 1
net.ipv4.tcp_fin_timeout = 15
net.core.somaxconn = 65535
net.core.netdev_max_backlog = 65535
INNER
sysctl --system

apt-get update
apt-get install -y ca-certificates curl sysstat

K6_VERSION=$(curl -fsSL https://api.github.com/repos/grafana/k6/releases/latest | grep -m1 '"tag_name"' | cut -d'"' -f4)
curl -fsSL -o /tmp/k6.tar.gz "https://github.com/grafana/k6/releases/download/${K6_VERSION}/k6-${K6_VERSION}-linux-arm64.tar.gz"
tar -xzf /tmp/k6.tar.gz -C /tmp
install -m 0755 "/tmp/k6-${K6_VERSION}-linux-arm64/k6" /usr/local/bin/k6
rm -rf /tmp/k6.tar.gz "/tmp/k6-${K6_VERSION}-linux-arm64"
k6 version

install -d -o ubuntu -g ubuntu -m 0755 /home/ubuntu/loadtest
