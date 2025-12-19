/**
 * RelationshipGraphDialog 组件
 * 关系图谱对话框，用于在模态框中展示角色关系图
 * Requirements: 13.1, 13.2, 13.3, 13.4, 13.5
 */

import { useEffect, useCallback } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/app/components/ui/dialog';
import { RelationshipGraph } from './RelationshipGraph';
import { useAssetStore } from '@/stores/asset-store';
import { useProjectStore } from '@/stores/project-store';
import { Loader2, Network } from 'lucide-react';
import type { GraphEdge } from '@/types';

interface RelationshipGraphDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onNodeClick?: (nodeId: string) => void;
  onEdgeClick?: (edge: GraphEdge) => void;
}

export function RelationshipGraphDialog({
  open,
  onOpenChange,
  onNodeClick,
  onEdgeClick,
}: RelationshipGraphDialogProps) {
  const currentProject = useProjectStore((state) => state.currentProject);
  const { relationshipGraph, isLoading, fetchRelationshipGraph } = useAssetStore();

  // 加载关系图数据
  useEffect(() => {
    if (open && currentProject && !relationshipGraph) {
      fetchRelationshipGraph(currentProject.id);
    }
  }, [open, currentProject, relationshipGraph, fetchRelationshipGraph]);

  // 处理节点点击
  const handleNodeClick = useCallback((nodeId: string) => {
    onNodeClick?.(nodeId);
  }, [onNodeClick]);

  // 处理边点击
  const handleEdgeClick = useCallback((edge: GraphEdge) => {
    onEdgeClick?.(edge);
  }, [onEdgeClick]);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-5xl h-[80vh] flex flex-col p-0">
        <DialogHeader className="px-6 py-4 border-b">
          <DialogTitle className="flex items-center gap-2">
            <Network className="w-5 h-5" />
            角色关系图谱
          </DialogTitle>
          <DialogDescription>
            可视化展示角色之间的关系网络，支持缩放、平移和交互
          </DialogDescription>
        </DialogHeader>
        <div className="flex-1 overflow-hidden">
          {isLoading ? (
            <div className="flex items-center justify-center h-full">
              <Loader2 className="w-8 h-8 animate-spin text-muted-foreground" />
              <span className="ml-2 text-muted-foreground">加载关系图谱...</span>
            </div>
          ) : relationshipGraph ? (
            <RelationshipGraph
              data={relationshipGraph}
              onNodeClick={handleNodeClick}
              onEdgeClick={handleEdgeClick}
              width={900}
              height={550}
            />
          ) : (
            <div className="flex items-center justify-center h-full text-muted-foreground">
              <div className="text-center">
                <Network className="w-12 h-12 mx-auto mb-2 opacity-50" />
                <p>暂无关系图数据</p>
                <p className="text-sm">请先创建角色并添加关系</p>
              </div>
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}

export default RelationshipGraphDialog;
