/**
 * RelationshipGraph 组件
 * 可视化展示角色之间的关系图谱
 * Requirements: 13.1, 13.2, 13.3, 13.4, 13.5
 */

import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import type { GraphNode, GraphEdge, RelationshipGraph as RelationshipGraphData } from '@/types';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from '@/app/components/ui/dialog';
import { Button } from '@/app/components/ui/button';
import { Badge } from '@/app/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/app/components/ui/card';
import { ScrollArea } from '@/app/components/ui/scroll-area';
import { ZoomIn, ZoomOut, Maximize2, User, Link2 } from 'lucide-react';

interface RelationshipGraphProps {
  data: RelationshipGraphData;
  onNodeClick?: (nodeId: string) => void;
  onEdgeClick?: (edge: GraphEdge) => void;
  width?: number;
  height?: number;
}

interface NodePosition {
  id: string;
  x: number;
  y: number;
  vx: number;
  vy: number;
}

interface HoveredNode {
  node: GraphNode;
  position: { x: number; y: number };
}

// 关系类型颜色映射
const RELATIONSHIP_COLORS: Record<string, string> = {
  '朋友': '#22c55e',
  '敌人': '#ef4444',
  '恋人': '#ec4899',
  '家人': '#f59e0b',
  '同事': '#3b82f6',
  '师徒': '#8b5cf6',
  '对手': '#f97316',
  'default': '#6b7280',
};

// 角色类型颜色映射
const ROLE_COLORS: Record<string, string> = {
  '主角': '#3b82f6',
  '配角': '#22c55e',
  '反派': '#ef4444',
  '龙套': '#9ca3af',
  'default': '#6b7280',
};

const getRelationshipColor = (type: string): string => {
  return RELATIONSHIP_COLORS[type] || RELATIONSHIP_COLORS.default;
};

const getRoleColor = (role: string): string => {
  return ROLE_COLORS[role] || ROLE_COLORS.default;
};

export function RelationshipGraph({
  data,
  onNodeClick,
  onEdgeClick,
  width = 800,
  height = 600,
}: RelationshipGraphProps) {
  const svgRef = useRef<SVGSVGElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  
  // 状态
  const [nodePositions, setNodePositions] = useState<NodePosition[]>([]);
  const [hoveredNode, setHoveredNode] = useState<HoveredNode | null>(null);
  const [selectedEdge, setSelectedEdge] = useState<GraphEdge | null>(null);
  const [zoom, setZoom] = useState(1);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [isDragging, setIsDragging] = useState(false);
  const [dragStart, setDragStart] = useState({ x: 0, y: 0 });
  const [draggedNodeId, setDraggedNodeId] = useState<string | null>(null);

  // 初始化节点位置（圆形布局）
  useEffect(() => {
    if (data.nodes.length === 0) return;

    const centerX = width / 2;
    const centerY = height / 2;
    const radius = Math.min(width, height) / 3;

    const positions: NodePosition[] = data.nodes.map((node, index) => {
      const angle = (2 * Math.PI * index) / data.nodes.length;
      return {
        id: node.id,
        x: centerX + radius * Math.cos(angle),
        y: centerY + radius * Math.sin(angle),
        vx: 0,
        vy: 0,
      };
    });

    setNodePositions(positions);
  }, [data.nodes, width, height]);

  // 力导向布局模拟
  useEffect(() => {
    if (nodePositions.length === 0) return;

    const simulate = () => {
      setNodePositions((prevPositions) => {
        const newPositions = prevPositions.map((pos) => ({ ...pos }));
        const centerX = width / 2;
        const centerY = height / 2;

        // 斥力（节点之间）
        for (let i = 0; i < newPositions.length; i++) {
          for (let j = i + 1; j < newPositions.length; j++) {
            const dx = newPositions[j].x - newPositions[i].x;
            const dy = newPositions[j].y - newPositions[i].y;
            const dist = Math.sqrt(dx * dx + dy * dy) || 1;
            const force = 5000 / (dist * dist);
            const fx = (dx / dist) * force;
            const fy = (dy / dist) * force;

            newPositions[i].vx -= fx;
            newPositions[i].vy -= fy;
            newPositions[j].vx += fx;
            newPositions[j].vy += fy;
          }
        }

        // 引力（连接的节点之间）
        data.edges.forEach((edge) => {
          const sourceIdx = newPositions.findIndex((p) => p.id === edge.source);
          const targetIdx = newPositions.findIndex((p) => p.id === edge.target);
          if (sourceIdx === -1 || targetIdx === -1) return;

          const dx = newPositions[targetIdx].x - newPositions[sourceIdx].x;
          const dy = newPositions[targetIdx].y - newPositions[sourceIdx].y;
          const dist = Math.sqrt(dx * dx + dy * dy) || 1;
          const force = (dist - 150) * 0.01;
          const fx = (dx / dist) * force;
          const fy = (dy / dist) * force;

          newPositions[sourceIdx].vx += fx;
          newPositions[sourceIdx].vy += fy;
          newPositions[targetIdx].vx -= fx;
          newPositions[targetIdx].vy -= fy;
        });

        // 向中心的引力
        newPositions.forEach((pos) => {
          const dx = centerX - pos.x;
          const dy = centerY - pos.y;
          pos.vx += dx * 0.001;
          pos.vy += dy * 0.001;
        });

        // 应用速度并衰减
        newPositions.forEach((pos) => {
          if (pos.id === draggedNodeId) return; // 跳过正在拖拽的节点
          pos.x += pos.vx;
          pos.y += pos.vy;
          pos.vx *= 0.9;
          pos.vy *= 0.9;

          // 边界约束
          pos.x = Math.max(50, Math.min(width - 50, pos.x));
          pos.y = Math.max(50, Math.min(height - 50, pos.y));
        });

        return newPositions;
      });
    };

    const interval = setInterval(simulate, 50);
    return () => clearInterval(interval);
  }, [nodePositions.length, data.edges, width, height, draggedNodeId]);

  // 获取节点位置
  const getNodePosition = useCallback(
    (nodeId: string) => {
      return nodePositions.find((p) => p.id === nodeId) || { x: 0, y: 0 };
    },
    [nodePositions]
  );

  // 获取连接到指定节点的所有边
  const getConnectedEdges = useCallback(
    (nodeId: string) => {
      return data.edges.filter((e) => e.source === nodeId || e.target === nodeId);
    },
    [data.edges]
  );

  // 获取连接到指定节点的所有节点ID
  const getConnectedNodeIds = useCallback(
    (nodeId: string) => {
      const edges = getConnectedEdges(nodeId);
      const ids = new Set<string>();
      edges.forEach((e) => {
        if (e.source === nodeId) ids.add(e.target);
        if (e.target === nodeId) ids.add(e.source);
      });
      return ids;
    },
    [getConnectedEdges]
  );

  // 处理缩放
  const handleZoomIn = () => setZoom((z) => Math.min(z + 0.2, 3));
  const handleZoomOut = () => setZoom((z) => Math.max(z - 0.2, 0.3));
  const handleResetView = () => {
    setZoom(1);
    setPan({ x: 0, y: 0 });
  };

  // 处理平移
  const handleMouseDown = (e: React.MouseEvent) => {
    if (e.target === svgRef.current) {
      setIsDragging(true);
      setDragStart({ x: e.clientX - pan.x, y: e.clientY - pan.y });
    }
  };

  const handleMouseMove = (e: React.MouseEvent) => {
    if (isDragging && !draggedNodeId) {
      setPan({
        x: e.clientX - dragStart.x,
        y: e.clientY - dragStart.y,
      });
    } else if (draggedNodeId) {
      const rect = svgRef.current?.getBoundingClientRect();
      if (!rect) return;
      const x = (e.clientX - rect.left - pan.x) / zoom;
      const y = (e.clientY - rect.top - pan.y) / zoom;
      setNodePositions((prev) =>
        prev.map((p) => (p.id === draggedNodeId ? { ...p, x, y, vx: 0, vy: 0 } : p))
      );
    }
  };

  const handleMouseUp = () => {
    setIsDragging(false);
    setDraggedNodeId(null);
  };

  // 处理节点拖拽
  const handleNodeMouseDown = (e: React.MouseEvent, nodeId: string) => {
    e.stopPropagation();
    setDraggedNodeId(nodeId);
  };

  // 处理节点悬停
  const handleNodeHover = (node: GraphNode, e: React.MouseEvent) => {
    const rect = containerRef.current?.getBoundingClientRect();
    if (!rect) return;
    setHoveredNode({
      node,
      position: {
        x: e.clientX - rect.left,
        y: e.clientY - rect.top,
      },
    });
  };

  // 处理边点击
  const handleEdgeClick = (edge: GraphEdge) => {
    setSelectedEdge(edge);
    onEdgeClick?.(edge);
  };

  // 高亮状态
  const highlightedNodeIds = useMemo(() => {
    if (!hoveredNode) return new Set<string>();
    const ids = getConnectedNodeIds(hoveredNode.node.id);
    ids.add(hoveredNode.node.id);
    return ids;
  }, [hoveredNode, getConnectedNodeIds]);

  const highlightedEdges = useMemo(() => {
    if (!hoveredNode) return new Set<string>();
    const edges = getConnectedEdges(hoveredNode.node.id);
    return new Set(edges.map((e) => `${e.source}-${e.target}`));
  }, [hoveredNode, getConnectedEdges]);

  // 获取边的源节点和目标节点名称
  const getEdgeNodeNames = (edge: GraphEdge) => {
    const sourceNode = data.nodes.find((n) => n.id === edge.source);
    const targetNode = data.nodes.find((n) => n.id === edge.target);
    return {
      sourceName: sourceNode?.name || '未知',
      targetName: targetNode?.name || '未知',
    };
  };

  if (data.nodes.length === 0) {
    return (
      <div className="flex items-center justify-center h-full text-muted-foreground">
        <div className="text-center">
          <User className="w-12 h-12 mx-auto mb-2 opacity-50" />
          <p>暂无角色数据</p>
          <p className="text-sm">创建角色后即可查看关系图谱</p>
        </div>
      </div>
    );
  }

  return (
    <div ref={containerRef} className="relative w-full h-full overflow-hidden bg-background">
      {/* 工具栏 */}
      <div className="absolute top-4 right-4 z-10 flex gap-2">
        <Button variant="outline" size="icon" onClick={handleZoomIn} title="放大">
          <ZoomIn className="w-4 h-4" />
        </Button>
        <Button variant="outline" size="icon" onClick={handleZoomOut} title="缩小">
          <ZoomOut className="w-4 h-4" />
        </Button>
        <Button variant="outline" size="icon" onClick={handleResetView} title="重置视图">
          <Maximize2 className="w-4 h-4" />
        </Button>
      </div>

      {/* 图例 */}
      <div className="absolute bottom-4 left-4 z-10 bg-background/90 backdrop-blur-sm rounded-lg p-3 border">
        <p className="text-xs font-medium mb-2">图例</p>
        <div className="space-y-1">
          {Object.entries(ROLE_COLORS).filter(([k]) => k !== 'default').map(([role, color]) => (
            <div key={role} className="flex items-center gap-2 text-xs">
              <div className="w-3 h-3 rounded-full" style={{ backgroundColor: color }} />
              <span>{role}</span>
            </div>
          ))}
        </div>
      </div>

      {/* SVG 图谱 */}
      <svg
        ref={svgRef}
        width={width}
        height={height}
        className="cursor-grab active:cursor-grabbing"
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
      >
        <g transform={`translate(${pan.x}, ${pan.y}) scale(${zoom})`}>
          {/* 边 */}
          {data.edges.map((edge) => {
            const sourcePos = getNodePosition(edge.source);
            const targetPos = getNodePosition(edge.target);
            const edgeKey = `${edge.source}-${edge.target}`;
            const isHighlighted = highlightedEdges.has(edgeKey);
            const opacity = hoveredNode ? (isHighlighted ? 1 : 0.2) : 0.6;

            // 计算边的中点用于显示标签
            const midX = (sourcePos.x + targetPos.x) / 2;
            const midY = (sourcePos.y + targetPos.y) / 2;

            return (
              <g key={edgeKey}>
                <line
                  x1={sourcePos.x}
                  y1={sourcePos.y}
                  x2={targetPos.x}
                  y2={targetPos.y}
                  stroke={getRelationshipColor(edge.type)}
                  strokeWidth={isHighlighted ? 3 : 2}
                  opacity={opacity}
                  className="cursor-pointer transition-all"
                  onClick={() => handleEdgeClick(edge)}
                />
                {/* 双向箭头指示 */}
                {edge.bidirectional && (
                  <circle
                    cx={midX}
                    cy={midY}
                    r={4}
                    fill={getRelationshipColor(edge.type)}
                    opacity={opacity}
                  />
                )}
                {/* 关系类型标签 */}
                <text
                  x={midX}
                  y={midY - 8}
                  textAnchor="middle"
                  className="text-xs fill-muted-foreground pointer-events-none"
                  opacity={opacity}
                >
                  {edge.type}
                </text>
              </g>
            );
          })}

          {/* 节点 */}
          {data.nodes.map((node) => {
            const pos = getNodePosition(node.id);
            const isHighlighted = highlightedNodeIds.has(node.id);
            const opacity = hoveredNode ? (isHighlighted ? 1 : 0.3) : 1;
            const nodeColor = getRoleColor(node.role);

            return (
              <g
                key={node.id}
                transform={`translate(${pos.x}, ${pos.y})`}
                className="cursor-pointer"
                onMouseDown={(e) => handleNodeMouseDown(e, node.id)}
                onMouseEnter={(e) => handleNodeHover(node, e)}
                onMouseLeave={() => setHoveredNode(null)}
                onClick={() => onNodeClick?.(node.id)}
                opacity={opacity}
              >
                {/* 节点背景 */}
                <circle
                  r={isHighlighted ? 35 : 30}
                  fill={nodeColor}
                  className="transition-all"
                  stroke={isHighlighted ? '#fff' : 'transparent'}
                  strokeWidth={3}
                />
                {/* 节点文字 */}
                <text
                  textAnchor="middle"
                  dy="0.35em"
                  className="text-sm font-medium fill-white pointer-events-none"
                >
                  {node.name.length > 4 ? node.name.slice(0, 4) + '...' : node.name}
                </text>
                {/* 角色类型标签 */}
                <text
                  textAnchor="middle"
                  y={45}
                  className="text-xs fill-muted-foreground pointer-events-none"
                >
                  {node.role}
                </text>
                {/* 非活跃状态指示 */}
                {!node.isActive && (
                  <circle
                    r={35}
                    fill="none"
                    stroke="#9ca3af"
                    strokeWidth={2}
                    strokeDasharray="5,5"
                  />
                )}
              </g>
            );
          })}
        </g>
      </svg>

      {/* 节点悬停提示 */}
      {hoveredNode && (
        <Card
          className="absolute z-20 w-64 shadow-lg"
          style={{
            left: Math.min(hoveredNode.position.x + 20, width - 280),
            top: Math.min(hoveredNode.position.y + 20, height - 200),
          }}
        >
          <CardHeader className="pb-2">
            <CardTitle className="text-base flex items-center gap-2">
              <div
                className="w-3 h-3 rounded-full"
                style={{ backgroundColor: getRoleColor(hoveredNode.node.role) }}
              />
              {hoveredNode.node.name}
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            <div className="flex items-center gap-2">
              <Badge variant="outline">{hoveredNode.node.role}</Badge>
              {hoveredNode.node.archetype && (
                <Badge variant="secondary">{hoveredNode.node.archetype}</Badge>
              )}
            </div>
            <p className="text-xs text-muted-foreground">
              状态: {hoveredNode.node.isActive ? '活跃' : '非活跃'}
            </p>
            <div className="text-xs text-muted-foreground">
              <p className="font-medium">关系:</p>
              <ScrollArea className="h-20">
                {getConnectedEdges(hoveredNode.node.id).map((edge, idx) => {
                  const { sourceName, targetName } = getEdgeNodeNames(edge);
                  const otherName = edge.source === hoveredNode.node.id ? targetName : sourceName;
                  return (
                    <p key={idx} className="flex items-center gap-1">
                      <span
                        className="w-2 h-2 rounded-full"
                        style={{ backgroundColor: getRelationshipColor(edge.type) }}
                      />
                      {edge.type}: {otherName}
                    </p>
                  );
                })}
                {getConnectedEdges(hoveredNode.node.id).length === 0 && (
                  <p className="text-muted-foreground">暂无关系</p>
                )}
              </ScrollArea>
            </div>
          </CardContent>
        </Card>
      )}

      {/* 边详情对话框 */}
      <Dialog open={!!selectedEdge} onOpenChange={() => setSelectedEdge(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Link2 className="w-5 h-5" />
              关系详情
            </DialogTitle>
            <DialogDescription>
              查看角色之间的关系信息
            </DialogDescription>
          </DialogHeader>
          {selectedEdge && (
            <div className="space-y-4">
              <div className="flex items-center justify-center gap-4">
                <div className="text-center">
                  <div
                    className="w-12 h-12 rounded-full mx-auto mb-2 flex items-center justify-center text-white font-medium"
                    style={{ backgroundColor: getRoleColor(data.nodes.find((n) => n.id === selectedEdge.source)?.role || '') }}
                  >
                    {data.nodes.find((n) => n.id === selectedEdge.source)?.name.slice(0, 2)}
                  </div>
                  <p className="text-sm font-medium">
                    {data.nodes.find((n) => n.id === selectedEdge.source)?.name}
                  </p>
                </div>
                <div className="flex flex-col items-center">
                  <Badge style={{ backgroundColor: getRelationshipColor(selectedEdge.type) }}>
                    {selectedEdge.type}
                  </Badge>
                  {selectedEdge.bidirectional && (
                    <span className="text-xs text-muted-foreground mt-1">双向</span>
                  )}
                </div>
                <div className="text-center">
                  <div
                    className="w-12 h-12 rounded-full mx-auto mb-2 flex items-center justify-center text-white font-medium"
                    style={{ backgroundColor: getRoleColor(data.nodes.find((n) => n.id === selectedEdge.target)?.role || '') }}
                  >
                    {data.nodes.find((n) => n.id === selectedEdge.target)?.name.slice(0, 2)}
                  </div>
                  <p className="text-sm font-medium">
                    {data.nodes.find((n) => n.id === selectedEdge.target)?.name}
                  </p>
                </div>
              </div>
              {selectedEdge.description && (
                <div className="bg-muted p-3 rounded-lg">
                  <p className="text-sm font-medium mb-1">关系描述</p>
                  <p className="text-sm text-muted-foreground">{selectedEdge.description}</p>
                </div>
              )}
              {selectedEdge.strength !== undefined && (
                <div className="flex items-center gap-2">
                  <span className="text-sm">关系强度:</span>
                  <div className="flex-1 h-2 bg-muted rounded-full overflow-hidden">
                    <div
                      className="h-full rounded-full"
                      style={{
                        width: `${selectedEdge.strength * 100}%`,
                        backgroundColor: getRelationshipColor(selectedEdge.type),
                      }}
                    />
                  </div>
                  <span className="text-sm text-muted-foreground">
                    {Math.round(selectedEdge.strength * 100)}%
                  </span>
                </div>
              )}
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}

export default RelationshipGraph;
