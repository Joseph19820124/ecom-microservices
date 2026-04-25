import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import { Construct } from 'constructs';

export class NetworkStack extends cdk.Stack {
  public readonly vpc: ec2.Vpc;
  public readonly cluster: ecs.Cluster;
  public readonly alb: elbv2.ApplicationLoadBalancer;
  public readonly listener: elbv2.ApplicationListener;
  public readonly albSecurityGroup: ec2.SecurityGroup;
  public readonly vpcLinkSecurityGroup: ec2.SecurityGroup;

  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    this.vpc = new ec2.Vpc(this, 'EcomVpc', {
      maxAzs: 2,
      natGateways: 1,
      subnetConfiguration: [
        { name: 'public',  cidrMask: 24, subnetType: ec2.SubnetType.PUBLIC },
        { name: 'private', cidrMask: 24, subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
      ],
    });

    this.cluster = new ecs.Cluster(this, 'EcomCluster', {
      vpc: this.vpc,
      containerInsightsV2: ecs.ContainerInsights.ENABLED,
      clusterName: 'ecom-cluster',
    });

    this.vpcLinkSecurityGroup = new ec2.SecurityGroup(this, 'VpcLinkSg', {
      vpc: this.vpc,
      description: 'Security group for API Gateway VPC Link ENIs',
      allowAllOutbound: true,
    });

    this.albSecurityGroup = new ec2.SecurityGroup(this, 'AlbSg', {
      vpc: this.vpc,
      description: 'Security group for the internal ALB',
      allowAllOutbound: true,
    });

    // Only the API Gateway VPC Link can hit the internal ALB on port 80.
    this.albSecurityGroup.addIngressRule(
      this.vpcLinkSecurityGroup,
      ec2.Port.tcp(80),
      'Allow inbound from API Gateway VPC Link only',
    );

    this.alb = new elbv2.ApplicationLoadBalancer(this, 'EcomInternalAlb', {
      vpc: this.vpc,
      internetFacing: false,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
      securityGroup: this.albSecurityGroup,
      loadBalancerName: 'ecom-internal-alb',
    });

    this.listener = new elbv2.ApplicationListener(this, 'AlbListener', {
      loadBalancer: this.alb,
      port: 80,
      protocol: elbv2.ApplicationProtocol.HTTP,
      defaultAction: elbv2.ListenerAction.fixedResponse(404, {
        contentType: 'application/json',
        messageBody: JSON.stringify({ error: 'route not found', service: 'ecom-alb' }),
      }),
    });

    new cdk.CfnOutput(this, 'AlbDnsName',     { value: this.alb.loadBalancerDnsName });
    new cdk.CfnOutput(this, 'VpcId',          { value: this.vpc.vpcId });
    new cdk.CfnOutput(this, 'ClusterName',    { value: this.cluster.clusterName });
    new cdk.CfnOutput(this, 'AlbListenerArn', { value: this.listener.listenerArn, exportName: 'EcomAlbListenerArn' });
    new cdk.CfnOutput(this, 'VpcLinkSgId',    { value: this.vpcLinkSecurityGroup.securityGroupId, exportName: 'EcomVpcLinkSgId' });
  }
}
