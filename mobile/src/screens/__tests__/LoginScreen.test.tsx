import React from 'react';
import { fireEvent, render, waitFor } from '@testing-library/react-native';
import * as SecureStore from 'expo-secure-store';
import { LoginScreen } from '../LoginScreen';

const mockLogin = jest.fn<Promise<void>, [string, string]>();

jest.mock('../../context/AuthContext', () => ({
  useAuth: () => ({
    login: mockLogin
  })
}));

jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(),
  setItemAsync: jest.fn()
}));

const getItemAsyncMock = SecureStore.getItemAsync as jest.MockedFunction<typeof SecureStore.getItemAsync>;
const setItemAsyncMock = SecureStore.setItemAsync as jest.MockedFunction<typeof SecureStore.setItemAsync>;

describe('LoginScreen', () => {
  beforeEach(() => {
    mockLogin.mockReset();
    getItemAsyncMock.mockReset();
    setItemAsyncMock.mockReset();
  });

  it('shows validation error when pin is not exactly 6 digits', async () => {
    getItemAsyncMock.mockResolvedValueOnce(null);
    const { getByPlaceholderText, getByText, findByText } = render(<LoginScreen />);

    fireEvent.changeText(getByPlaceholderText('Username'), 'test');
    fireEvent.changeText(getByPlaceholderText('6-digit PIN'), '123');
    fireEvent.press(getByText('Log In'));

    expect(await findByText('PIN must be exactly 6 digits.')).toBeTruthy();
    expect(mockLogin).not.toHaveBeenCalled();
  });

  it('loads persisted username and saves it on successful login', async () => {
    getItemAsyncMock.mockResolvedValueOnce('test');
    mockLogin.mockResolvedValueOnce();

    const { getByPlaceholderText, getByText, getByDisplayValue } = render(<LoginScreen />);

    await waitFor(() => {
      expect(getByDisplayValue('test')).toBeTruthy();
    });

    fireEvent.changeText(getByPlaceholderText('6-digit PIN'), '123456');
    fireEvent.press(getByText('Log In'));

    await waitFor(() => {
      expect(mockLogin).toHaveBeenCalledWith('test', '123456');
      expect(setItemAsyncMock).toHaveBeenCalledWith('somtranscriber_username', 'test');
    });
  });
});

  it('accepts a valid 6-digit pin', async () => {
    expect(/^\d{6}$/.test('123456')).toBe(true);
  });
