import * as cdk from 'aws-cdk-lib';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import { Construct } from 'constructs';
import { ServiceDef } from './services';

export interface EcrStackProps extends cdk.StackProps {
  services: ServiceDef[];
}

export class EcrStack extends cdk.Stack {
  public readonly repositories: Record<string, ecr.IRepository> = {};

  constructor(scope: Construct, id: string, props: EcrStackProps) {
    super(scope, id, props);

    for (const svc of props.services) {
      const repo = new ecr.Repository(this, `Repo-${svc.name}`, {
        repositoryName: svc.repoName,
        imageScanOnPush: true,
        imageTagMutability: ecr.TagMutability.MUTABLE,
        removalPolicy: cdk.RemovalPolicy.DESTROY,
        emptyOnDelete: true,
        lifecycleRules: [
          { maxImageCount: 20, description: 'Keep last 20 images' },
        ],
      });
      this.repositories[svc.name] = repo;

      new cdk.CfnOutput(this, `RepoUri-${svc.name}`, {
        value: repo.repositoryUri,
        exportName: `EcomRepoUri-${svc.name}`,
      });
    }
  }
}
