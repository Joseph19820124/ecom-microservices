#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { NetworkStack } from '../lib/network-stack';
import { EcrStack } from '../lib/ecr-stack';
import { EcsStack } from '../lib/ecs-stack';
import { ApiStack } from '../lib/api-stack';
import { SERVICES } from '../lib/services';

const app = new cdk.App();

const env = {
  account: process.env.CDK_DEFAULT_ACCOUNT ?? process.env.AWS_ACCOUNT_ID,
  region: process.env.CDK_DEFAULT_REGION ?? process.env.AWS_REGION ?? 'ap-northeast-2',
};

const ecr = new EcrStack(app, 'EcomEcrStack', {
  env,
  services: SERVICES,
});

const network = new NetworkStack(app, 'EcomNetworkStack', { env });

const ecs = new EcsStack(app, 'EcomEcsStack', {
  env,
  vpc: network.vpc,
  alb: network.alb,
  listener: network.listener,
  cluster: network.cluster,
  repositories: ecr.repositories,
  services: SERVICES,
});
ecs.addDependency(ecr);
ecs.addDependency(network);

const api = new ApiStack(app, 'EcomApiStack', {
  env,
  vpc: network.vpc,
  alb: network.alb,
  listener: network.listener,
  vpcLinkSecurityGroup: network.vpcLinkSecurityGroup,
  services: SERVICES,
});
api.addDependency(ecs);

app.synth();
