import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as apigw from 'aws-cdk-lib/aws-apigatewayv2';
import * as integrations from 'aws-cdk-lib/aws-apigatewayv2-integrations';
import { Construct } from 'constructs';
import { ServiceDef } from './services';

export interface ApiStackProps extends cdk.StackProps {
  vpc: ec2.IVpc;
  alb: elbv2.IApplicationLoadBalancer;
  listener: elbv2.IApplicationListener;
  vpcLinkSecurityGroup: ec2.ISecurityGroup;
  services: ServiceDef[];
}

export class ApiStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: ApiStackProps) {
    super(scope, id, props);

    // VPC Link sits in the private subnets and uses the SG that the ALB allows ingress from.
    const vpcLink = new apigw.VpcLink(this, 'EcomVpcLink', {
      vpc: props.vpc,
      vpcLinkName: 'ecom-vpc-link',
      subnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
      securityGroups: [props.vpcLinkSecurityGroup],
    });

    const httpApi = new apigw.HttpApi(this, 'EcomHttpApi', {
      apiName: 'ecom-http-api',
      description: 'Public ingress for ecommerce microservices',
      corsPreflight: {
        allowHeaders: ['*'],
        allowMethods: [apigw.CorsHttpMethod.ANY],
        allowOrigins: ['*'],
        maxAge: cdk.Duration.days(1),
      },
    });

    const albIntegration = new integrations.HttpAlbIntegration('AlbIntegration', props.listener, {
      vpcLink,
    });

    for (const svc of props.services) {
      httpApi.addRoutes({
        path: `${svc.pathPrefix}`,
        methods: [apigw.HttpMethod.ANY],
        integration: albIntegration,
      });
      httpApi.addRoutes({
        path: `${svc.pathPrefix}/{proxy+}`,
        methods: [apigw.HttpMethod.ANY],
        integration: albIntegration,
      });
    }

    new cdk.CfnOutput(this, 'ApiUrl', {
      value: httpApi.apiEndpoint,
      description: 'Public API Gateway endpoint',
      exportName: 'EcomApiUrl',
    });
  }
}
