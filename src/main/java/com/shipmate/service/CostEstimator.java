package com.shipmate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipmate.config.AgentProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CostEstimator {

    private final AgentProperties props;
    private final ObjectMapper mapper;

    public CostEstimator(AgentProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    public static class Sizing {
        public int vcpu;
        public int memoryGiB;
        public int replicas;
        public int maxReplicas;
        public String instanceType;
        public String dbType;
        public int dbStorageGiB;
        public int dbWriteRpm;

        public Sizing(int vcpu, int memoryGiB, int replicas, int maxReplicas, String instanceType,
                      String dbType, int dbStorageGiB, int dbWriteRpm) {
            this.vcpu = vcpu;
            this.memoryGiB = memoryGiB;
            this.replicas = replicas;
            this.maxReplicas = maxReplicas;
            this.instanceType = instanceType;
            this.dbType = dbType;
            this.dbStorageGiB = dbStorageGiB;
            this.dbWriteRpm = dbWriteRpm;
        }
    }

    public Sizing computeSizing(int rpm) {
        int vcpu = Math.max(1, (int) Math.ceil(rpm / 200.0));
        int memoryGiB = Math.max(1, vcpu);
        int replicas = Math.max(1, (int) Math.ceil(vcpu / 2.0));
        int maxReplicas = replicas * 2;

        var instances = props.getPricing().getInstances();
        AgentProperties.Pricing.InstancePrice chosen = instances.get(0);
        for (var i : instances) {
            if (i.getVcpu() >= (int) Math.ceil(vcpu / (double) replicas)
                    && i.getMemoryGiB() >= (int) Math.ceil(memoryGiB / (double) replicas)) {
                chosen = i;
                break;
            }
        }

        int writeRpm = Math.max(10, (int) Math.ceil(rpm * 0.2));
        var dbs = props.getPricing().getDatabases();
        AgentProperties.Pricing.DbPrice db = dbs.get(0);
        for (var d : dbs) {
            if (d.getMaxWriteRpm() >= writeRpm) {
                db = d;
                break;
            }
        }

        return new Sizing(vcpu, memoryGiB, replicas, maxReplicas, chosen.getName(), db.getName(), db.getStorageGiB(), writeRpm);
    }

    public List<Map<String, Object>> breakdown(int rpm) {
        Sizing s = computeSizing(rpm);
        var pricing = props.getPricing();
        double buffer = pricing.getBuffer();

        AgentProperties.Pricing.InstancePrice instance = findInstance(s.instanceType);
        AgentProperties.Pricing.DbPrice db = findDb(s.dbType);

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item("EKS control plane", "managed k8s master, always-on", pricing.getOverhead().getControlPlane()));
        items.add(item("Worker nodes", s.replicas + " × " + instance.getName() + " (" + instance.getVcpu() + " vCPU / "
                        + instance.getMemoryGiB() + " GiB) — computed from " + rpm + " RPM ÷ 200",
                round(s.replicas * instance.getPriceMonthly() * buffer)));
        items.add(item("PostgreSQL", db.getName() + " — sized for ~" + s.dbWriteRpm + " write RPM (20% of traffic)",
                round(db.getPriceMonthly() * buffer)));
        items.add(item("Block storage", "gp3 × " + s.dbStorageGiB + " GiB @ $" + pricing.getStoragePerGiB() + "/GiB",
                round(s.dbStorageGiB * pricing.getStoragePerGiB())));
        items.add(item("NAT gateway", "outbound traffic for cluster nodes", pricing.getOverhead().getNatGateway()));
        items.add(item("Load balancer", "one ALB for public HTTP traffic", pricing.getOverhead().getLoadBalancer()));
        items.add(item("Container registry", "ECR storage + data transfer", pricing.getOverhead().getRegistry()));

        double total = items.stream().mapToDouble(i -> (double) i.get("monthly")).sum();
        items.add(item("TOTAL", "estimated monthly (staging-sized, " + pricing.getBuffer() + "× buffer)", round(total)));

        return items;
    }

    public double deltaForReplicas(int currentReplicas, int newReplicas) {
        Sizing s = computeSizing(100);
        AgentProperties.Pricing.InstancePrice instance = findInstance(s.instanceType);
        double buffer = props.getPricing().getBuffer();
        return round((newReplicas - currentReplicas) * instance.getPriceMonthly() * buffer);
    }

    public double replicaCost(int replicas) {
        Sizing s = computeSizing(100);
        AgentProperties.Pricing.InstancePrice instance = findInstance(s.instanceType);
        double buffer = props.getPricing().getBuffer();
        return round(replicas * instance.getPriceMonthly() * buffer);
    }

    private AgentProperties.Pricing.InstancePrice findInstance(String name) {
        return props.getPricing().getInstances().stream()
                .filter(i -> i.getName().equals(name))
                .findFirst()
                .orElse(props.getPricing().getInstances().get(0));
    }

    private AgentProperties.Pricing.DbPrice findDb(String name) {
        return props.getPricing().getDatabases().stream()
                .filter(d -> d.getName().equals(name))
                .findFirst()
                .orElse(props.getPricing().getDatabases().get(0));
    }

    private Map<String, Object> item(String name, String detail, double monthly) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("detail", detail);
        m.put("monthly", monthly);
        return m;
    }

    public static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public String toJson(List<Map<String, Object>> items) {
        try {
            return mapper.writeValueAsString(items);
        } catch (Exception e) {
            return "[]";
        }
    }
}
