const request = require('supertest');
const express = require('express');
const jwt = require('jsonwebtoken');
const friendRoutes = require('../friend');
const { getPool } = require('../../config/database');
const { sendToUser } = require('../../websocket/socketHandler');

// Mock dependencies
jest.mock('../../config/database');
jest.mock('../../websocket/socketHandler');
jest.mock('../../utils/logger', () => ({
  info: jest.fn(),
  error: jest.fn(),
  warn: jest.fn()
}));

const app = express();
app.use(express.json());
app.use('/api/friends', friendRoutes);

// Helper function to generate JWT token
function generateToken(userId) {
  return jwt.sign({ userId }, process.env.JWT_SECRET || 'test-secret', { expiresIn: '1h' });
}

describe('Friend Routes', () => {
  let mockPool;
  let mockQuery;
  
  beforeEach(() => {
    // Reset mocks
    jest.clearAllMocks();
    
    // Setup mock pool
    mockQuery = jest.fn();
    mockPool = {
      query: mockQuery
    };
    getPool.mockReturnValue(mockPool);
    
    // Mock sendToUser
    sendToUser.mockResolvedValue(undefined);
  });
  
  describe('POST /api/friends/request', () => {
    const validToken = generateToken('user-123');
    const toUserId = 'user-456';
    
    it('should send friend request successfully', async () => {
      // Mock database queries
      mockQuery
        .mockResolvedValueOnce([[{ user_id: toUserId }]]) // Check user exists
        .mockResolvedValueOnce([[]]) // Check not already friends
        .mockResolvedValueOnce([[]]) // Check no pending request
        .mockResolvedValueOnce([{ insertId: 1 }]) // Insert friend request
        .mockResolvedValueOnce([[{ user_id: 'user-123', nickname: 'Test User', avatar: null }]]); // Get sender info
      
      const response = await request(app)
        .post('/api/friends/request')
        .set('Authorization', `Bearer ${validToken}`)
        .send({
          toUserId: toUserId,
          message: 'Hello, friend!'
        });
      
      expect(response.status).toBe(201);
      expect(response.body).toHaveProperty('requestId');
      expect(response.body).toHaveProperty('message', '好友请求已发送');
      
      // Verify sendToUser was called
      expect(sendToUser).toHaveBeenCalledWith(
        toUserId,
        'friend_request',
        expect.objectContaining({
          fromUserId: 'user-123',
          fromUserNickname: 'Test User',
          message: 'Hello, friend!'
        })
      );
    });
    
    it('should reject if trying to add self as friend', async () => {
      const response = await request(app)
        .post('/api/friends/request')
        .set('Authorization', `Bearer ${validToken}`)
        .send({
          toUserId: 'user-123', // Same as sender
          message: 'Hello'
        });
      
      expect(response.status).toBe(400);
      expect(response.body).toHaveProperty('error', '不能添加自己为好友');
    });
    
    it('should reject if user does not exist', async () => {
      mockQuery.mockResolvedValueOnce([[]]); // User not found
      
      const response = await request(app)
        .post('/api/friends/request')
        .set('Authorization', `Bearer ${validToken}`)
        .send({
          toUserId: 'non-existent-user',
          message: 'Hello'
        });
      
      expect(response.status).toBe(404);
      expect(response.body).toHaveProperty('error', '用户不存在');
    });
    
    it('should reject if already friends', async () => {
      mockQuery
        .mockResolvedValueOnce([[{ user_id: toUserId }]]) // User exists
        .mockResolvedValueOnce([[{ id: 1 }]]); // Already friends
      
      const response = await request(app)
        .post('/api/friends/request')
        .set('Authorization', `Bearer ${validToken}`)
        .send({
          toUserId: toUserId,
          message: 'Hello'
        });
      
      expect(response.status).toBe(400);
      expect(response.body).toHaveProperty('error', '已经是好友关系');
    });
    
    it('should reject if pending request exists', async () => {
      mockQuery
        .mockResolvedValueOnce([[{ user_id: toUserId }]]) // User exists
        .mockResolvedValueOnce([[]]) // Not friends
        .mockResolvedValueOnce([[{ request_id: 'existing-request' }]]); // Pending request exists
      
      const response = await request(app)
        .post('/api/friends/request')
        .set('Authorization', `Bearer ${validToken}`)
        .send({
          toUserId: toUserId,
          message: 'Hello'
        });
      
      expect(response.status).toBe(400);
      expect(response.body).toHaveProperty('error', '已发送好友请求，等待对方处理');
    });
    
    it('should handle WebSocket notification failure gracefully', async () => {
      // Mock sendToUser to throw error
      sendToUser.mockRejectedValueOnce(new Error('WebSocket error'));
      
      mockQuery
        .mockResolvedValueOnce([[{ user_id: toUserId }]])
        .mockResolvedValueOnce([[]])
        .mockResolvedValueOnce([[]])
        .mockResolvedValueOnce([{ insertId: 1 }])
        .mockResolvedValueOnce([[{ user_id: 'user-123', nickname: 'Test User', avatar: null }]]);
      
      const response = await request(app)
        .post('/api/friends/request')
        .set('Authorization', `Bearer ${validToken}`)
        .send({
          toUserId: toUserId,
          message: 'Hello'
        });
      
      // Should still succeed even if WebSocket fails
      expect(response.status).toBe(201);
      expect(response.body).toHaveProperty('requestId');
    });
  });
  
  describe('POST /api/friends/accept', () => {
    const validToken = generateToken('user-456');
    const requestId = 'request-123';
    
    it('should accept friend request successfully', async () => {
      mockQuery
        .mockResolvedValueOnce([[[
          {
            request_id: requestId,
            from_user_id: 'user-123',
            to_user_id: 'user-456',
            status: 'PENDING'
          }
        ]]]) // Find request
        .mockResolvedValueOnce([{ affectedRows: 1 }]) // Update request status
        .mockResolvedValueOnce([{ insertId: 1 }]) // Insert friend relationship (user-456 -> user-123)
        .mockResolvedValueOnce([{ insertId: 2 }]); // Insert friend relationship (user-123 -> user-456)
      
      const response = await request(app)
        .post('/api/friends/accept')
        .set('Authorization', `Bearer ${validToken}`)
        .query({ requestId: requestId });
      
      expect(response.status).toBe(200);
      expect(response.body).toHaveProperty('message', '好友请求已接受');
    });
    
    it('should reject if request does not exist', async () => {
      mockQuery.mockResolvedValueOnce([[]]); // Request not found
      
      const response = await request(app)
        .post('/api/friends/accept')
        .set('Authorization', `Bearer ${validToken}`)
        .query({ requestId: 'non-existent' });
      
      expect(response.status).toBe(404);
      expect(response.body).toHaveProperty('error', '好友请求不存在或已处理');
    });
  });
  
  describe('POST /api/friends/reject', () => {
    const validToken = generateToken('user-456');
    const requestId = 'request-123';
    
    it('should reject friend request successfully', async () => {
      mockQuery.mockResolvedValueOnce([{ affectedRows: 1 }]);
      
      const response = await request(app)
        .post('/api/friends/reject')
        .set('Authorization', `Bearer ${validToken}`)
        .query({ requestId: requestId });
      
      expect(response.status).toBe(200);
      expect(response.body).toHaveProperty('message', '好友请求已拒绝');
    });
    
    it('should return 404 if request does not exist', async () => {
      mockQuery.mockResolvedValueOnce([{ affectedRows: 0 }]);
      
      const response = await request(app)
        .post('/api/friends/reject')
        .set('Authorization', `Bearer ${validToken}`)
        .query({ requestId: 'non-existent' });
      
      expect(response.status).toBe(404);
      expect(response.body).toHaveProperty('error', '好友请求不存在或已处理');
    });
  });
  
  describe('GET /api/friends/requests', () => {
    const validToken = generateToken('user-123');
    
    it('should return friend requests list', async () => {
      const receivedRequests = [
        {
          request_id: 'req-1',
          from_user_id: 'user-456',
          message: 'Hello',
          created_at: new Date(),
          nickname: 'Friend 1',
          avatar: null
        }
      ];
      
      const sentRequests = [
        {
          request_id: 'req-2',
          to_user_id: 'user-789',
          message: 'Hi',
          status: 'PENDING',
          created_at: new Date(),
          nickname: 'Friend 2',
          avatar: null
        }
      ];
      
      mockQuery
        .mockResolvedValueOnce([receivedRequests])
        .mockResolvedValueOnce([sentRequests]);
      
      const response = await request(app)
        .get('/api/friends/requests')
        .set('Authorization', `Bearer ${validToken}`);
      
      expect(response.status).toBe(200);
      expect(response.body).toHaveProperty('received');
      expect(response.body).toHaveProperty('sent');
      expect(response.body.received).toHaveLength(1);
      expect(response.body.sent).toHaveLength(1);
    });
  });
});


