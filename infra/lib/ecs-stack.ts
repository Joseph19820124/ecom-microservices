import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as logs from 'aws-cdk-lib/aws-logs';
import { Construct } from 'constructs';
import { ServiceDef } from './services';

export interface EcsStackProps extends cdk.StackProps {
  vpc: ec2.IVpc;
  alb: elbv2.IApplicationLoadBalancer;
  listener: elbv2.IApplicationListener;
  cluster: ecs.ICluster;
  repositories: Record<string, ecr.IRepository>;
  services: ServiceDef[];
}

export class EcsStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: EcsStackProps) {
    super(scope, id, props);

    const listener = props.listener;

    new cdk.CfnOutput(this, 'AlbListenerArn', {
      value: listener.listenerArn,
      exportName: 'EcomAlbListenerArn',
    });

    for (const svc of props.services) {
      const repo = props.repositories[svc.name];

      const taskDef = new ecs.FargateTaskDefinition(this, `Td-${svc.name}`, {
        cpu: svc.cpu,
        memoryLimitMiB: svc.memoryMiB,
        runtimePlatform: {
          cpuArchitecture: ecs.CpuArchitecture.X86_64,
          operatingSystemFamily: ecs.OperatingSystemFamily.LINUX,
        },
      });

      const container = taskDef.addContainer(`${svc.name}-container`, {
        image: ecs.ContainerImage.fromEcrRepository(repo, 'latest'),
        logging: ecs.LogDrivers.awsLogs({
          streamPrefix: svc.name,
          logRetention: logs.RetentionDays.ONE_WEEK,
        }),
        environment: {
          SPRING_PROFILES_ACTIVE: 'prod',
          SERVICE_NAME: svc.name,
        },
        portMappings: [{ containerPort: svc.containerPort, protocol: ecs.Protocol.TCP }],
      });

      const serviceSg = new ec2.SecurityGroup(this, `Sg-${svc.name}`, {
        vpc: props.vpc,
        description: `Security group for ${svc.name}-service tasks`,
        allowAllOutbound: true,
      });

      // Tasks live in private subnets; allow the container port from anywhere inside the VPC
      // (only the internal ALB and the VPC link can reach them anyway).
      serviceSg.addIngressRule(ec2.Peer.ipv4(props.vpc.vpcCidrBlock), ec2.Port.tcp(svc.containerPort));

      const fargate = new ecs.FargateService(this, `Svc-${svc.name}`, {
        cluster: props.cluster,
        taskDefinition: taskDef,
        desiredCount: svc.desiredCount,
        assignPublicIp: false,
        vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
        securityGroups: [serviceSg],
        serviceName: `${svc.name}-service`,
        circuitBreaker: { rollback: true },
        minHealthyPercent: 50,
        maxHealthyPercent: 200,
        healthCheckGracePeriod: cdk.Duration.seconds(60),
      });

      const targetGroup = new elbv2.ApplicationTargetGroup(this, `Tg-${svc.name}`, {
        vpc: props.vpc,
        port: svc.containerPort,
        protocol: elbv2.ApplicationProtocol.HTTP,
        targetType: elbv2.TargetType.IP,
        deregistrationDelay: cdk.Duration.seconds(20),
        healthCheck: {
          path: svc.healthCheck,
          interval: cdk.Duration.seconds(15),
          timeout: cdk.Duration.seconds(5),
          healthyThresholdCount: 2,
          unhealthyThresholdCount: 3,
          healthyHttpCodes: '200-399',
        },
      });

      fargate.attachToApplicationTargetGroup(targetGroup);

      new elbv2.ApplicationListenerRule(this, `Rule-${svc.name}`, {
        listener,
        priority: svc.priority,
        conditions: [elbv2.ListenerCondition.pathPatterns([`${svc.pathPrefix}`, `${svc.pathPrefix}/*`])],
        action: elbv2.ListenerAction.forward([targetGroup]),
      });
    }
  }
}
